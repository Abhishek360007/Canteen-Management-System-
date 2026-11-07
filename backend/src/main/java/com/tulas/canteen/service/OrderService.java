package com.tulas.canteen.service;

import com.tulas.canteen.config.ApiException;
import com.tulas.canteen.domain.*;
import com.tulas.canteen.dto.OrderDtos.CartLine;
import com.tulas.canteen.dto.OrderDtos.OrderView;
import com.tulas.canteen.dto.OrderDtos.PlaceOrderRequest;
import com.tulas.canteen.repo.DailyCounterRepository;
import com.tulas.canteen.repo.MenuItemRepository;
import com.tulas.canteen.repo.OrderRepository;
import com.tulas.canteen.repo.UserRepository;
import com.tulas.canteen.security.AuthUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class OrderService {

    public static final List<OrderStatus> ACTIVE = List.of(OrderStatus.PLACED, OrderStatus.PREPARING, OrderStatus.READY);
    private static final List<OrderStatus> IN_KITCHEN = List.of(OrderStatus.PLACED, OrderStatus.PREPARING);
    static final int MAX_OTP_ATTEMPTS = 5;
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    private final OrderRepository orders;
    private final MenuItemRepository menu;
    private final UserRepository users;
    private final DailyCounterRepository counters;
    private final WalletService wallet;
    private final SettingsService settings;
    private final LiveHub live;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public OrderService(OrderRepository orders, MenuItemRepository menu, UserRepository users,
                        DailyCounterRepository counters, WalletService wallet, SettingsService settings,
                        LiveHub live, Clock clock) {
        this.orders = orders;
        this.menu = menu;
        this.users = users;
        this.counters = counters;
        this.wallet = wallet;
        this.settings = settings;
        this.live = live;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- student

    /**
     * Places an order as one transaction: check rules → reserve plates → take payment → save.
     * If any step fails, everything (plates, money) is rolled back together.
     * The same Idempotency-Key returns the first order instead of creating a duplicate,
     * which protects against double taps and retries on bad Wi-Fi.
     */
    @Transactional
    public OrderView place(AuthUser me, PlaceOrderRequest req, String idempotencyKey) {
        String key = idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey.trim();
        if (key != null && key.length() > 64) throw ApiException.badRequest("Idempotency-Key is too long.");
        if (key != null) {
            Optional<Order> existing = orders.findByUserIdAndIdempotencyKey(me.id(), key);
            if (existing.isPresent()) return studentView(orders.findFull(existing.get().getId()).orElseThrow());
        }

        CanteenSettings rules = settings.get();
        if (!settings.isOpenNow(rules)) {
            throw ApiException.badRequest("Tula's Canteen isn't taking orders right now (open "
                    + rules.getOpenTime().format(HM) + "–" + rules.getCloseTime().format(HM) + ").");
        }
        if (req.paymentMethod() == PaymentMethod.CASH && !rules.isCashAllowed()) {
            throw ApiException.badRequest("Cash orders are switched off. Please pay from your wallet.");
        }
        if (orders.countByUserIdAndStatusIn(me.id(), ACTIVE) >= rules.getMaxActiveOrders()) {
            throw ApiException.badRequest("You already have " + rules.getMaxActiveOrders()
                    + " orders in progress. Collect one before ordering again.");
        }

        // Merge repeated lines for the same dish.
        Map<Long, Integer> qty = new LinkedHashMap<>();
        for (CartLine line : req.items()) qty.merge(line.menuItemId(), line.quantity(), Integer::sum);

        Map<Long, MenuItem> dishes = new HashMap<>();
        menu.findAllById(qty.keySet()).forEach(m -> dishes.put(m.getId(), m));

        BigDecimal total = BigDecimal.ZERO;
        int prep = 0;
        for (var e : qty.entrySet()) {
            MenuItem m = dishes.get(e.getKey());
            if (m == null || m.isArchived()) throw ApiException.badRequest("A dish in your cart is no longer on the menu.");
            if (!m.isAvailable()) throw ApiException.badRequest(m.getName() + " isn't available right now.");
            if (e.getValue() > rules.getMaxQtyPerItem()) {
                throw ApiException.badRequest("You can order at most " + rules.getMaxQtyPerItem() + " of " + m.getName() + ".");
            }
            if (m.getStockToday() != null && m.getStockToday() < e.getValue()) {
                throw ApiException.badRequest(m.getStockToday() == 0
                        ? m.getName() + " is sold out."
                        : "Only " + m.getStockToday() + " plate(s) of " + m.getName() + " left.");
            }
            total = total.add(m.getPrice().multiply(BigDecimal.valueOf(e.getValue())));
            prep = Math.max(prep, m.getPrepMinutes());
        }

        LocalTime slot = parseSlot(req.pickupSlot(), rules);
        LocalDate today = clock.today();
        String code = nextCode(today);

        // Reserve plates. Each reservation is a conditional UPDATE, so two students can't buy the last plate.
        for (var e : qty.entrySet()) {
            MenuItem m = dishes.get(e.getKey());
            if (m.getStockToday() != null && menu.reserve(m.getId(), e.getValue()) == 0) {
                throw ApiException.conflict(m.getName() + " just sold out. Please update your cart.");
            }
        }

        if (req.paymentMethod() == PaymentMethod.WALLET) {
            wallet.pay(me.id(), total, code);
        }

        Order o = new Order();
        o.setUser(users.getReferenceById(me.id()));
        o.setCode(code);
        o.setOrderDate(today);
        o.setPaymentMethod(req.paymentMethod());
        o.setTotal(total);
        o.setOtp(String.format("%04d", random.nextInt(10_000)));
        o.setPickupSlot(slot);
        o.setNote(req.note() == null || req.note().isBlank() ? null : req.note().trim());
        o.setEstPrepMinutes(Math.max(prep, 1));
        o.setIdempotencyKey(key);
        qty.forEach((id, q) -> o.addItem(new OrderItem(dishes.get(id), q)));
        orders.save(o);

        notifyChange(o);
        live.toEveryone("menu", Map.of("changed", true));
        return studentView(o);
    }

    @Transactional(readOnly = true)
    public List<OrderView> myOrders(AuthUser me) {
        return orders.findTop50ByUserIdOrderByCreatedAtDesc(me.id()).stream().map(this::studentView).toList();
    }

    @Transactional(readOnly = true)
    public OrderView get(AuthUser me, Long id) {
        Order o = orders.findFull(id).orElseThrow(() -> ApiException.notFound("Order not found"));
        if (o.getUser().getId().equals(me.id())) return studentView(o);
        if (me.isStaffOrAdmin()) return OrderView.staff(o);
        throw ApiException.notFound("Order not found");
    }

    /** Students may cancel only before the kitchen starts cooking. */
    @Transactional
    public OrderView cancelByStudent(AuthUser me, Long id) {
        Order o = ownOrder(me, id);
        if (o.getStatus() != OrderStatus.PLACED) {
            throw ApiException.badRequest("The kitchen has already started this order, so it can't be cancelled.");
        }
        cancel(o, "Cancelled by you", true);
        return studentView(o);
    }

    @Transactional
    public OrderView rate(AuthUser me, Long id, int stars) {
        Order o = ownOrder(me, id);
        if (o.getStatus() != OrderStatus.COLLECTED) throw ApiException.badRequest("You can rate an order after collecting it.");
        if (o.getRating() != null) throw ApiException.badRequest("You've already rated this order.");
        o.setRating(stars);
        menu.findAllById(o.getItems().stream().map(OrderItem::getMenuItemId).toList())
                .forEach(m -> m.addRating(stars));
        return studentView(o);
    }

    // ---------------------------------------------------------------- kitchen

    @Transactional(readOnly = true)
    public List<OrderView> kitchenQueue() {
        return orders.findByOrderDateAndStatusInOrderByCreatedAtAsc(clock.today(), ACTIVE)
                .stream().map(OrderView::staff).toList();
    }

    @Transactional(readOnly = true)
    public List<OrderView> today() {
        return orders.findByOrderDateOrderByCreatedAtDesc(clock.today()).stream().map(OrderView::staff).toList();
    }

    @Transactional
    public OrderView updateStatus(Long id, OrderStatus next, String reason) {
        Order o = orders.findFull(id).orElseThrow(() -> ApiException.notFound("Order not found"));
        OrderStatus cur = o.getStatus();
        if (!cur.isActive()) throw ApiException.conflict("Order " + o.getCode() + " is already " + label(cur) + ".");

        switch (next) {
            case PREPARING -> {
                if (cur != OrderStatus.PLACED) throw badMove(cur, next);
                o.setStatus(next);
            }
            case READY -> {
                if (cur == OrderStatus.READY) throw badMove(cur, next);
                o.setStatus(next);
                o.setReadyAt(Instant.now());
            }
            case COLLECTED -> {
                // Manual hand-over without OTP, e.g. the student's phone is dead. Staff decide.
                if (cur != OrderStatus.READY) throw ApiException.badRequest("Mark the order ready before handing it over.");
                close(o, OrderStatus.COLLECTED);
            }
            case NO_SHOW -> {
                if (cur != OrderStatus.READY) throw badMove(cur, next);
                markNoShow(o);
            }
            case CANCELLED -> cancel(o, reason == null || reason.isBlank() ? "Cancelled by the canteen" : reason.trim(),
                    cur == OrderStatus.PLACED);
            default -> throw badMove(cur, next);
        }
        notifyChange(o);
        return OrderView.staff(o);
    }

    public record VerifyResult(boolean ok, String message, Integer attemptsLeft, OrderView order) {}

    /**
     * Hand-over check at the counter. The student shows the pass; staff type (or scan) the order code and OTP.
     * Wrong codes are counted and saved even though the request fails, so guessing is limited.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public VerifyResult verify(String rawCode, String otp) {
        String code = normaliseCode(rawCode);
        Order o = orders.findByOrderDateAndCodeIgnoreCase(clock.today(), code)
                .orElseThrow(() -> ApiException.notFound("No order " + code + " today."));
        if (!o.getStatus().isActive()) {
            throw ApiException.conflict("Order " + o.getCode() + " is already " + label(o.getStatus()) + ".");
        }
        if (o.getOtpAttempts() >= MAX_OTP_ATTEMPTS) {
            throw new ApiException(HttpStatus.LOCKED,
                    "Too many wrong codes for " + o.getCode() + ". Check the student's pass and use manual hand-over.");
        }
        if (!o.getOtp().equals(otp)) {
            o.setOtpAttempts(o.getOtpAttempts() + 1);
            int left = MAX_OTP_ATTEMPTS - o.getOtpAttempts();
            return new VerifyResult(false, "Wrong code. " + left + " attempt(s) left.", left, OrderView.staff(o));
        }
        if (o.getStatus() != OrderStatus.READY) o.setReadyAt(Instant.now());
        close(o, OrderStatus.COLLECTED);
        notifyChange(o);
        return new VerifyResult(true, "Hand over " + o.getCode() + " to " + o.getUser().getName() + ".", null,
                OrderView.staff(o));
    }

    // ---------------------------------------------------------------- jobs (called by OrderJobs)

    @Transactional
    public int markNoShows() {
        int minutes = settings.get().getNoShowMinutes();
        List<Order> late = orders.findByStatusAndReadyAtBefore(OrderStatus.READY, Instant.now().minusSeconds(minutes * 60L));
        late.forEach(o -> {
            markNoShow(o);
            notifyChange(o);
        });
        return late.size();
    }

    /** Orders left unfinished from previous days: cancel and refund. */
    @Transactional
    public int cancelStale() {
        List<Order> stale = orders.findByStatusInAndOrderDateBefore(ACTIVE, clock.today());
        stale.forEach(o -> {
            cancel(o, "Not completed before the canteen closed", o.getStatus() == OrderStatus.PLACED);
            notifyChange(o);
        });
        return stale.size();
    }

    // ---------------------------------------------------------------- helpers

    private void cancel(Order o, String reason, boolean returnPlates) {
        if (returnPlates) releasePlates(o);
        if (o.getPaymentMethod() == PaymentMethod.WALLET) wallet.refund(o.getUser().getId(), o.getTotal(), o.getCode());
        o.setCancelReason(reason);
        close(o, OrderStatus.CANCELLED);
    }

    /** Not collected in time: the food was cooked, so no refund, but the plates go back on sale. */
    private void markNoShow(Order o) {
        releasePlates(o);
        o.setCancelReason("Not collected within the hold time");
        close(o, OrderStatus.NO_SHOW);
    }

    private void releasePlates(Order o) {
        o.getItems().forEach(i -> menu.release(i.getMenuItemId(), i.getQuantity()));
        live.toEveryone("menu", Map.of("changed", true));
    }

    private void close(Order o, OrderStatus status) {
        o.setStatus(status);
        o.setClosedAt(Instant.now());
    }

    private void notifyChange(Order o) {
        Map<String, Object> ev = Map.of("id", o.getId(), "code", o.getCode(), "status", o.getStatus());
        live.toStaff("order", ev);
        live.toUser(o.getUser().getId(), "order", ev);
    }

    private Order ownOrder(AuthUser me, Long id) {
        Order o = orders.findFull(id).orElseThrow(() -> ApiException.notFound("Order not found"));
        if (!o.getUser().getId().equals(me.id())) throw ApiException.notFound("Order not found");
        return o;
    }

    private OrderView studentView(Order o) {
        Long ahead = null;
        Integer eta = null;
        if (IN_KITCHEN.contains(o.getStatus())) {
            ahead = orders.countAhead(o.getOrderDate(), IN_KITCHEN, o.getId());
            int base = o.getStatus() == OrderStatus.PLACED ? o.getEstPrepMinutes() : Math.max(2, o.getEstPrepMinutes() / 2);
            eta = base + (int) (ahead * 2);
        } else if (o.getStatus() == OrderStatus.READY) {
            eta = 0;
        }
        return OrderView.of(o, o.getStatus().isActive(), ahead, eta);
    }

    private LocalTime parseSlot(String raw, CanteenSettings rules) {
        if (raw == null || raw.isBlank()) return null;
        LocalTime slot = LocalTime.parse(raw, HM);
        if (slot.isBefore(clock.now().minusMinutes(5))) throw ApiException.badRequest("That pickup time has already passed.");
        if (!slot.isBefore(rules.getCloseTime())) {
            throw ApiException.badRequest("Pickup must be before closing time (" + rules.getCloseTime().format(HM) + ").");
        }
        return slot;
    }

    /** Next order number for the day. The row lock makes concurrent orders queue up for a number. */
    private String nextCode(LocalDate day) {
        DailyCounter c = counters.lockDay(day).orElseGet(() -> counters.saveAndFlush(new DailyCounter(day)));
        return String.format("T-%03d", c.next());
    }

    /** Accepts "T-017", "t017", "t 17" or "17" and returns "T-017". */
    static String normaliseCode(String raw) {
        String digits = raw == null ? "" : raw.replaceAll("\\D", "");
        if (digits.isEmpty() || digits.length() > 6) throw ApiException.badRequest("Enter a valid order number, e.g. T-017.");
        return String.format("T-%03d", Integer.parseInt(digits));
    }

    private static ApiException badMove(OrderStatus from, OrderStatus to) {
        return ApiException.badRequest("Can't change an order from " + label(from) + " to " + label(to) + ".");
    }

    private static String label(OrderStatus s) {
        return s.name().toLowerCase().replace('_', '-');
    }
}
