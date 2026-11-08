package com.tulas.canteen.web;

import com.tulas.canteen.security.AuthUser;
import com.tulas.canteen.service.AuditService;
import com.tulas.canteen.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Online payment (Razorpay) for students, the Razorpay webhook, and the admin payment settings. */
@RestController
public class PaymentController {

    private final PaymentService payments;
    private final AuditService audit;

    public PaymentController(PaymentService payments, AuditService audit) {
        this.payments = payments;
        this.audit = audit;
    }

    // ---- students

    @GetMapping("/api/payments/config")
    public PaymentService.Config config() {
        return payments.config();
    }

    public record StartBody(@NotNull @DecimalMin("1.00") @DecimalMax("100000.00") @Digits(integer = 6, fraction = 2) BigDecimal amount,
                            @Pattern(regexp = "^(wallet|checkout)?$") String purpose) {}

    @PostMapping("/api/payments/razorpay/order")
    public PaymentService.CheckoutOrder start(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody StartBody b) {
        return payments.start(me, b.amount(), b.purpose());
    }

    public record ConfirmBody(@NotBlank @Size(max = 40) String orderId, @NotBlank @Size(max = 40) String paymentId,
                              @NotBlank @Size(max = 128) String signature) {}

    @PostMapping("/api/payments/razorpay/verify")
    public PaymentService.Result confirm(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody ConfirmBody b) {
        return payments.confirm(me, b.orderId(), b.paymentId(), b.signature());
    }

    public record CancelBody(@NotBlank @Size(max = 40) String orderId) {}

    @PostMapping("/api/payments/razorpay/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelled(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody CancelBody b) {
        payments.cancelled(me, b.orderId());
    }

    @GetMapping("/api/payments/mine")
    public List<PaymentService.PaymentView> mine(@AuthenticationPrincipal AuthUser me) {
        return payments.mine(me.id());
    }

    /** Called by Razorpay's servers (no login); trusted only if the X-Razorpay-Signature matches. */
    @PostMapping("/api/payments/razorpay/webhook")
    public ResponseEntity<Map<String, String>> webhook(@RequestBody String body,
                                                       @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        return payments.webhook(body, signature)
                ? ResponseEntity.ok(Map.of("status", "ok"))
                : ResponseEntity.badRequest().body(Map.of("status", "invalid signature"));
    }

    // ---- admin

    @GetMapping("/api/admin/payments/settings")
    public PaymentService.AdminSettings settings() {
        return payments.adminSettings();
    }

    public record SettingsBody(boolean onlineEnabled, @Min(1) @Max(100000) int minTopup, @Min(1) @Max(100000) int maxTopup) {}

    @PutMapping("/api/admin/payments/settings")
    public PaymentService.AdminSettings updateSettings(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody SettingsBody b) {
        PaymentService.AdminSettings s = payments.updateSettings(b.onlineEnabled(), b.minTopup(), b.maxTopup());
        audit.record(me, "SETTINGS_CHANGED", "Online payments",
                (b.onlineEnabled() ? "On" : "Off") + " · top-ups ₹" + b.minTopup() + "–₹" + b.maxTopup());
        return s;
    }

    @GetMapping("/api/admin/payments")
    public List<PaymentService.PaymentView> recent() {
        return payments.recent();
    }
}
