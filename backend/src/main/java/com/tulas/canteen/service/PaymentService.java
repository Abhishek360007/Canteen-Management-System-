package com.tulas.canteen.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tulas.canteen.config.ApiException;
import com.tulas.canteen.domain.CanteenSettings;
import com.tulas.canteen.domain.OnlinePayment;
import com.tulas.canteen.domain.Role;
import com.tulas.canteen.domain.User;
import com.tulas.canteen.repo.OnlinePaymentRepository;
import com.tulas.canteen.repo.SettingsRepository;
import com.tulas.canteen.repo.UserRepository;
import com.tulas.canteen.security.AuthUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Online payments with Razorpay, always into the student's wallet:
 * <ol>
 *   <li>The app asks the server to create a Razorpay order for the amount (the amount is fixed here, on the server).</li>
 *   <li>The student pays in Razorpay's checkout (UPI, card, netbanking, wallets).</li>
 *   <li>The wallet is credited once the payment is proven: by the signature Razorpay gives the browser, or by
 *       Razorpay's signed webhook if the browser was closed. Whichever arrives first credits; the other is ignored.</li>
 * </ol>
 * Paying "online at checkout" is the same top-up for the amount missing, after which the order is paid from the wallet,
 * so refunds and rewards work exactly as for any wallet order.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    static final int MAX_ATTEMPTS_PER_HOUR = 10;

    private final RazorpayGateway razorpay;
    private final OnlinePaymentRepository payments;
    private final UserRepository users;
    private final SettingsRepository settingsRepo;
    private final SettingsService settings;
    private final WalletService wallet;
    private final NotificationService notifications;
    private final ObjectMapper json;

    public PaymentService(RazorpayGateway razorpay, OnlinePaymentRepository payments, UserRepository users,
                          SettingsRepository settingsRepo, SettingsService settings, WalletService wallet,
                          NotificationService notifications, ObjectMapper json) {
        this.razorpay = razorpay;
        this.payments = payments;
        this.users = users;
        this.settingsRepo = settingsRepo;
        this.settings = settings;
        this.wallet = wallet;
        this.notifications = notifications;
        this.json = json;
    }

    // ---------------------------------------------------------------- what students see

    public record Config(boolean online, String keyId, String mode, int minTopup, int maxTopup,
                        boolean upiManual, String upiId, String upiName) {}

    @Transactional(readOnly = true)
    public Config config() {
        CanteenSettings s = settings.get();
        boolean online = s.isOnlinePaymentsEnabled() && razorpay.configured();
        return new Config(online, online ? razorpay.keyId() : null, razorpay.mode(), s.getMinTopup(), s.getMaxTopup(),
                s.getUpiId() != null, s.getUpiId(), s.getUpiName());
    }

    public record CheckoutOrder(String orderId, long amount, String currency, String keyId, String name,
                                String description, Map<String, String> prefill) {}

    /** Step 1: create the Razorpay order. "checkout" top-ups may be below the minimum (they cover a shortfall). */
    @Transactional
    public CheckoutOrder start(AuthUser me, BigDecimal rupees, String purpose) {
        Config cfg = config();
        if (!cfg.online()) throw ApiException.badRequest("Online payment is switched off. Please use another way to pay.");
        User u = users.findById(me.id()).orElseThrow();
        if (u.getRole() != Role.STUDENT || u.isBlocked()) throw ApiException.forbidden("Only students can add money to a wallet.");
        boolean checkout = "checkout".equals(purpose);
        BigDecimal amount = rupees.setScale(2, RoundingMode.UP);
        BigDecimal min = checkout ? BigDecimal.ONE : BigDecimal.valueOf(cfg.minTopup());
        if (amount.compareTo(min) < 0 || amount.compareTo(BigDecimal.valueOf(cfg.maxTopup())) > 0) {
            throw ApiException.badRequest("Enter an amount between ₹" + min.stripTrailingZeros().toPlainString()
                    + " and ₹" + cfg.maxTopup() + ".");
        }
        if (payments.countByUserIdAndCreatedAtAfter(u.getId(), Instant.now().minus(1, ChronoUnit.HOURS)) >= MAX_ATTEMPTS_PER_HOUR) {
            throw ApiException.badRequest("Too many payment attempts. Please try again in a while.");
        }
        long paise = amount.movePointRight(2).longValueExact();
        String receipt = "w" + u.getId() + "-" + System.currentTimeMillis();
        String orderId = razorpay.createOrder(paise, receipt, Map.of("userId", String.valueOf(u.getId()), "purpose", checkout ? "checkout" : "wallet"));

        OnlinePayment p = new OnlinePayment();
        p.setUser(u);
        p.setAmountPaise(paise);
        p.setRazorpayOrderId(orderId);
        p.setPurpose(checkout ? "checkout" : "wallet");
        payments.save(p);

        return new CheckoutOrder(orderId, paise, "INR", razorpay.keyId(), "Tula's Canteen",
                checkout ? "Pay for your order" : "Add money to your wallet",
                Map.of("name", u.getName(), "email", u.getEmail(), "contact", u.getPhone() == null ? "" : u.getPhone()));
    }

    public record Result(boolean credited, BigDecimal amount, BigDecimal balance) {}

    /** Step 3a: the browser reports success with Razorpay's signature; credit after checking it. */
    @Transactional
    public Result confirm(AuthUser me, String orderId, String paymentId, String signature) {
        OnlinePayment p = payments.findByRazorpayOrderId(orderId).orElseThrow(() -> ApiException.notFound("Payment not found"));
        if (!p.getUser().getId().equals(me.id())) throw ApiException.notFound("Payment not found");
        if (!razorpay.checkPaymentSignature(orderId, paymentId, signature)) {
            log.warn("Rejected a payment confirmation with a bad signature (order {})", orderId);
            throw ApiException.badRequest("We couldn't verify this payment. If money was taken, it will be added automatically or refunded by Razorpay.");
        }
        boolean credited = credit(p, paymentId, "checkout");
        return new Result(credited, rupees(p.getAmountPaise()), users.balanceOf(me.id()));
    }

    /** The checkout was closed or the payment failed in the browser. */
    @Transactional
    public void cancelled(AuthUser me, String orderId) {
        payments.findByRazorpayOrderId(orderId).filter(p -> p.getUser().getId().equals(me.id()))
                .ifPresent(p -> payments.markFailed(p.getId()));
    }

    /**
     * Step 3b: Razorpay's server tells us about the payment (also when the student closed the browser).
     * Returns false if the signature is wrong (the caller answers 400 and Razorpay retries).
     */
    @Transactional
    public boolean webhook(String rawBody, String signature) {
        if (!razorpay.checkWebhookSignature(rawBody, signature)) {
            log.warn("Rejected a Razorpay webhook with a bad signature");
            return false;
        }
        try {
            JsonNode root = json.readTree(rawBody);
            String event = root.path("event").asText();
            JsonNode pay = root.path("payload").path("payment").path("entity");
            String orderId = pay.path("order_id").asText(null);
            if (orderId == null) return true;
            var found = payments.findByRazorpayOrderId(orderId);
            if (found.isEmpty()) return true; // not one of ours (e.g. another app on the same account)
            OnlinePayment p = found.get();
            switch (event) {
                case "payment.captured", "order.paid" -> {
                    if (pay.path("amount").asLong() != p.getAmountPaise()) {
                        log.warn("Webhook amount {} doesn't match order {} ({}); not credited", pay.path("amount").asLong(), orderId, p.getAmountPaise());
                        return true;
                    }
                    credit(p, pay.path("id").asText(), "webhook");
                }
                case "payment.failed" -> payments.markFailed(p.getId());
                default -> { }
            }
            return true;
        } catch (Exception e) {
            log.warn("Couldn't read a Razorpay webhook: {}", e.getMessage());
            return true; // signature was fine; don't make Razorpay retry a body we can't read
        }
    }

    /** Credits the wallet the first time a payment is confirmed, and never again. */
    private boolean credit(OnlinePayment p, String paymentId, String by) {
        if (payments.markPaid(p.getId(), paymentId, by, Instant.now()) == 0) return false;
        BigDecimal amount = rupees(p.getAmountPaise());
        wallet.recharge(p.getUser().getId(), amount, "Online payment · " + paymentId, "Razorpay");
        notifications.notify(p.getUser().getId(), "TOPUP", AuditService.rupees(amount) + " added to your wallet",
                "Online payment received (" + paymentId + ").", "/wallet");
        return true;
    }

    private static BigDecimal rupees(long paise) {
        return BigDecimal.valueOf(paise).movePointLeft(2);
    }

    // ---------------------------------------------------------------- admin

    public record AdminSettings(boolean onlineEnabled, int minTopup, int maxTopup, String mode,
                                boolean keysConfigured, boolean webhookConfigured, boolean upiManual) {}

    @Transactional(readOnly = true)
    public AdminSettings adminSettings() {
        CanteenSettings s = settings.get();
        return new AdminSettings(s.isOnlinePaymentsEnabled(), s.getMinTopup(), s.getMaxTopup(), razorpay.mode(),
                razorpay.configured(), razorpay.webhookConfigured(), s.getUpiId() != null);
    }

    @Transactional
    public AdminSettings updateSettings(boolean onlineEnabled, int minTopup, int maxTopup) {
        if (minTopup > maxTopup) throw ApiException.badRequest("The minimum can't be more than the maximum.");
        CanteenSettings s = settingsRepo.findById(CanteenSettings.SINGLETON_ID).orElseGet(() -> settingsRepo.save(new CanteenSettings()));
        s.setOnlinePaymentsEnabled(onlineEnabled);
        s.setMinTopup(minTopup);
        s.setMaxTopup(maxTopup);
        return adminSettings();
    }

    public record PaymentView(Long id, String studentName, String studentEmail, BigDecimal amount, String status,
                              String purpose, String razorpayOrderId, String razorpayPaymentId, String confirmedBy,
                              Instant createdAt, Instant paidAt) {}

    @Transactional(readOnly = true)
    public List<PaymentView> recent() {
        return payments.findTop100ByOrderByCreatedAtDesc().stream().map(p -> new PaymentView(p.getId(), p.getUser().getName(),
                p.getUser().getEmail(), rupees(p.getAmountPaise()), p.getStatus().name(), p.getPurpose(), p.getRazorpayOrderId(),
                p.getRazorpayPaymentId(), p.getConfirmedBy(), p.getCreatedAt(), p.getPaidAt())).toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentView> mine(Long userId) {
        return payments.findTop20ByUserIdOrderByCreatedAtDesc(userId).stream().map(p -> new PaymentView(p.getId(), null, null,
                rupees(p.getAmountPaise()), p.getStatus().name(), p.getPurpose(), p.getRazorpayOrderId(), p.getRazorpayPaymentId(),
                p.getConfirmedBy(), p.getCreatedAt(), p.getPaidAt())).toList();
    }
}
