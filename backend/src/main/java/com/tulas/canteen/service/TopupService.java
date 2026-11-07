package com.tulas.canteen.service;

import com.tulas.canteen.config.ApiException;
import com.tulas.canteen.domain.CanteenSettings;
import com.tulas.canteen.domain.Role;
import com.tulas.canteen.domain.TopupRequest;
import com.tulas.canteen.domain.TopupRequest.Status;
import com.tulas.canteen.dto.AdminDtos.TopupView;
import com.tulas.canteen.repo.TopupRequestRepository;
import com.tulas.canteen.repo.UserRepository;
import com.tulas.canteen.security.AuthUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * UPI wallet top-ups: the student pays the canteen's UPI ID from any UPI app, then submits the amount and
 * the UPI reference (UTR). An admin matches it with the canteen's UPI app and approves it, which credits the wallet.
 */
@Service
public class TopupService {

    static final int MAX_PENDING_PER_STUDENT = 3;

    private final TopupRequestRepository repo;
    private final UserRepository users;
    private final WalletService wallet;
    private final SettingsService settings;
    private final AuditService audit;
    private final LiveHub live;
    private final NotificationService notifications;

    public TopupService(TopupRequestRepository repo, UserRepository users, WalletService wallet,
                        SettingsService settings, AuditService audit, LiveHub live,
                        NotificationService notifications) {
        this.notifications = notifications;
        this.repo = repo;
        this.users = users;
        this.wallet = wallet;
        this.settings = settings;
        this.audit = audit;
        this.live = live;
    }

    @Transactional
    public TopupView request(AuthUser me, BigDecimal amount, String rawRef) {
        CanteenSettings s = settings.get();
        if (s.getUpiId() == null || s.getUpiId().isBlank()) {
            throw ApiException.badRequest("UPI top-ups aren't set up yet. Please recharge at the canteen counter.");
        }
        String ref = rawRef.trim().toUpperCase();
        if (repo.existsByUpiRefIgnoreCase(ref)) {
            throw ApiException.conflict("This UPI reference has already been submitted.");
        }
        if (repo.countByUserIdAndStatus(me.id(), Status.PENDING) >= MAX_PENDING_PER_STUDENT) {
            throw ApiException.badRequest("You already have " + MAX_PENDING_PER_STUDENT
                    + " top-ups waiting for approval. Please wait for those first.");
        }
        TopupRequest t = new TopupRequest();
        t.setUser(users.getReferenceById(me.id()));
        t.setAmount(amount);
        t.setUpiRef(ref);
        repo.save(t);
        live.toStaff("topup", Map.of("id", t.getId()));
        return TopupView.of(t);
    }

    @Transactional(readOnly = true)
    public List<TopupView> mine(Long userId) {
        return repo.findTop20ByUserIdOrderByCreatedAtDesc(userId).stream().map(TopupView::of).toList();
    }

    @Transactional(readOnly = true)
    public List<TopupView> list(Status status) {
        return (status == null ? repo.findTop100ByOrderByCreatedAtDesc() : repo.findTop100ByStatusOrderByCreatedAtAsc(status))
                .stream().map(TopupView::of).toList();
    }

    /** Approve: credit the wallet. The @Version on the request makes a second, simultaneous approval fail. */
    @Transactional
    public TopupView approve(AuthUser admin, Long id) {
        TopupRequest t = pending(id);
        if (t.getUser().getRole() != Role.STUDENT) throw ApiException.badRequest("Only students have a wallet.");
        t.decide(Status.APPROVED, admin.name(), null);
        repo.saveAndFlush(t); // version check happens here, before money moves
        wallet.recharge(t.getUser().getId(), t.getAmount(), "UPI top-up · ref " + t.getUpiRef(), admin.name());
        audit.record(admin, "TOPUP_APPROVED", AuditService.who(t.getUser()),
                AuditService.rupees(t.getAmount()) + " · UPI ref " + t.getUpiRef());
        notify(t);
        return TopupView.of(t);
    }

    @Transactional
    public TopupView reject(AuthUser admin, Long id, String reason) {
        TopupRequest t = pending(id);
        String why = reason == null || reason.isBlank() ? "Payment not found" : reason.trim();
        t.decide(Status.REJECTED, admin.name(), why);
        repo.saveAndFlush(t);
        audit.record(admin, "TOPUP_REJECTED", AuditService.who(t.getUser()),
                AuditService.rupees(t.getAmount()) + " · UPI ref " + t.getUpiRef() + " · " + why);
        notify(t);
        return TopupView.of(t);
    }

    public long pendingCount() {
        return repo.countByStatus(Status.PENDING);
    }

    private TopupRequest pending(Long id) {
        TopupRequest t = repo.findWithUserById(id).orElseThrow(() -> ApiException.notFound("Top-up request not found"));
        if (t.getStatus() != Status.PENDING) {
            throw ApiException.conflict("This request was already " + t.getStatus().name().toLowerCase()
                    + (t.getDecidedBy() == null ? "." : " by " + t.getDecidedBy() + "."));
        }
        return t;
    }

    private void notify(TopupRequest t) {
        boolean ok = t.getStatus() == Status.APPROVED;
        notifications.notify(t.getUser().getId(), "TOPUP",
                ok ? AuditService.rupees(t.getAmount()) + " added to your wallet" : "UPI top-up not approved",
                ok ? "Your UPI payment (ref " + t.getUpiRef() + ") was confirmed."
                        : "Ref " + t.getUpiRef() + ": " + t.getReason() + ". Contact the canteen office if you were charged.",
                "/wallet");
        live.toUser(t.getUser().getId(), "topup", Map.of("id", t.getId(), "status", t.getStatus()));
        live.toStaff("topup", Map.of("id", t.getId()));
    }
}
