package com.tulas.canteen.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"order_date", "code"}),
                @UniqueConstraint(columnNames = {"user_id", "idempotency_key"})
        },
        indexes = {
                @Index(columnList = "order_date,status"),
                @Index(columnList = "user_id,created_at")
        })
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Short code shown to the student and called out at the counter, e.g. "T-017". Unique per day. */
    @Column(nullable = false, length = 12)
    private String code;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OrderStatus status = OrderStatus.PLACED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private PaymentMethod paymentMethod;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    /** 4-digit handover code. Only the student sees it. */
    @Column(nullable = false, length = 4)
    private String otp;

    @Column(nullable = false)
    private int otpAttempts = 0;

    /** Requested pickup time; null means "as soon as possible". */
    private LocalTime pickupSlot;

    @Column(length = 200)
    private String note;

    /** Reward points used at checkout, and the rupee discount they gave. total = items − discount. */
    private Integer pointsRedeemed;

    @Column(precision = 10, scale = 2)
    private BigDecimal discount;

    /** Points earned when the order was collected. */
    private Integer pointsEarned;

    public int getPointsRedeemed() { return pointsRedeemed == null ? 0 : pointsRedeemed; }
    public void setPointsRedeemed(int p) { this.pointsRedeemed = p; }
    public BigDecimal getDiscount() { return discount == null ? BigDecimal.ZERO : discount; }
    public void setDiscount(BigDecimal discount) { this.discount = discount; }
    public int getPointsEarned() { return pointsEarned == null ? 0 : pointsEarned; }
    public void setPointsEarned(int p) { this.pointsEarned = p; }

    /** Longest prep time among the dishes, copied at order time; used for the ETA. */
    @Column(nullable = false)
    private int estPrepMinutes = 10;

    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    /** 1-5 stars, set by the student after collecting. */
    private Integer rating;

    @Column(length = 200)
    private String cancelReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
    private Instant readyAt;
    private Instant closedAt;

    /** Optimistic lock: stops two staff phones from handing over the same order twice. */
    @Version
    private long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderItem> items = new ArrayList<>();

    public void addItem(OrderItem item) {
        item.setOrder(this);
        items.add(item);
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public LocalDate getOrderDate() { return orderDate; }
    public void setOrderDate(LocalDate orderDate) { this.orderDate = orderDate; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public String getOtp() { return otp; }
    public void setOtp(String otp) { this.otp = otp; }
    public int getOtpAttempts() { return otpAttempts; }
    public void setOtpAttempts(int otpAttempts) { this.otpAttempts = otpAttempts; }
    public LocalTime getPickupSlot() { return pickupSlot; }
    public void setPickupSlot(LocalTime pickupSlot) { this.pickupSlot = pickupSlot; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public int getEstPrepMinutes() { return estPrepMinutes; }
    public void setEstPrepMinutes(int estPrepMinutes) { this.estPrepMinutes = estPrepMinutes; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReadyAt() { return readyAt; }
    public void setReadyAt(Instant readyAt) { this.readyAt = readyAt; }
    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }
    public List<OrderItem> getItems() { return items; }
}
