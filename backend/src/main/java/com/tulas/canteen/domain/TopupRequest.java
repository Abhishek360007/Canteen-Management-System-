package com.tulas.canteen.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A student paid the canteen by UPI and asks for the money to be added to their wallet.
 * An admin checks the payment reference against the canteen's UPI app and approves or rejects it.
 */
@Entity
@Table(name = "topup_requests", indexes = {
        @Index(columnList = "status,created_at"),
        @Index(columnList = "user_id,created_at")
})
public class TopupRequest {

    public enum Status { PENDING, APPROVED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    /** UPI transaction reference (UTR) the student copied from their payment app. */
    @Column(nullable = false, length = 40, unique = true)
    private String upiRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.PENDING;

    @Column(length = 200)
    private String reason;

    @Column(length = 80)
    private String decidedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    private Instant decidedAt;

    /** Stops two admins approving the same request twice. */
    @Version
    private long version;

    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getUpiRef() { return upiRef; }
    public void setUpiRef(String upiRef) { this.upiRef = upiRef; }
    public Status getStatus() { return status; }
    public String getReason() { return reason; }
    public String getDecidedBy() { return decidedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDecidedAt() { return decidedAt; }

    public void decide(Status status, String by, String reason) {
        this.status = status;
        this.decidedBy = by;
        this.reason = reason;
        this.decidedAt = Instant.now();
    }
}
