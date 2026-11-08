package com.tulas.canteen.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * One online payment attempt through Razorpay, used to add money to a student's wallet
 * (directly, or for the amount missing at checkout). Credited to the wallet exactly once.
 */
@Entity
@Table(name = "online_payments", indexes = {
        @Index(columnList = "user_id,created_at"),
        @Index(columnList = "status,created_at")
})
public class OnlinePayment {

    public enum Status { CREATED, PAID, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    /** Amount in paise (₹1 = 100), as Razorpay counts it. */
    @Column(nullable = false)
    private long amountPaise;

    /** Razorpay order id (order_...), created before the student pays. */
    @Column(nullable = false, unique = true, length = 40)
    private String razorpayOrderId;

    /** Razorpay payment id (pay_...), known once the student has paid. */
    @Column(unique = true, length = 40)
    private String razorpayPaymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.CREATED;

    /** "wallet" or "checkout" (topping up the amount missing for an order). */
    @Column(nullable = false, length = 12)
    private String purpose = "wallet";

    /** How it was confirmed: "checkout" (signature from the browser) or "webhook" (Razorpay server call). */
    @Column(length = 12)
    private String confirmedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    private Instant paidAt;

    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public long getAmountPaise() { return amountPaise; }
    public void setAmountPaise(long amountPaise) { this.amountPaise = amountPaise; }
    public String getRazorpayOrderId() { return razorpayOrderId; }
    public void setRazorpayOrderId(String id) { this.razorpayOrderId = id; }
    public String getRazorpayPaymentId() { return razorpayPaymentId; }
    public Status getStatus() { return status; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public String getConfirmedBy() { return confirmedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPaidAt() { return paidAt; }
}
