package com.tulas.canteen.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A problem a student reported about one of their orders (wrong item, cold food…). One per order.
 * Admins reply in a thread and close it as resolved (optionally with a wallet refund) or rejected.
 */
@Entity
@Table(name = "complaints",
        uniqueConstraints = @UniqueConstraint(columnNames = "order_id"),
        indexes = {
                @Index(columnList = "status,updated_at"),
                @Index(columnList = "user_id,created_at")
        })
public class Complaint {

    public enum Category { WRONG_ITEM, MISSING_ITEM, QUALITY, QUANTITY, HYGIENE, LATE, OVERCHARGED, STAFF, OTHER }

    public enum Status { OPEN, RESOLVED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    /** Copied from the order so admins can filter by canteen without a join. */
    @Column(name = "canteen_id")
    private Long canteenId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.OPEN;

    @Column(nullable = false, length = 600)
    private String details;

    /** Names of the dishes the problem is about, comma separated (empty = the whole order). */
    @Column(length = 300)
    private String dishes;

    /** Wallet refund given when it was resolved. */
    @Column(precision = 10, scale = 2)
    private BigDecimal refundAmount;

    @Column(length = 300)
    private String resolution;

    @Column(length = 80)
    private String resolvedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /** Last message or status change, for sorting. */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    private Instant resolvedAt;

    /** Who wrote last: true = the canteen, false = the student. Drives "waiting for reply" on each side. */
    @Column(nullable = false)
    private boolean lastFromStaff = false;

    /** Stops two admins from resolving (and refunding) the same complaint twice. */
    @Version
    private long version;

    public Long getId() { return id; }
    public Order getOrder() { return order; }
    public void setOrder(Order order) { this.order = order; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Long getCanteenId() { return canteenId; }
    public void setCanteenId(Long canteenId) { this.canteenId = canteenId; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public Status getStatus() { return status; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public String getDishes() { return dishes; }
    public void setDishes(String dishes) { this.dishes = dishes; }
    public BigDecimal getRefundAmount() { return refundAmount; }
    public String getResolution() { return resolution; }
    public String getResolvedBy() { return resolvedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public boolean isLastFromStaff() { return lastFromStaff; }

    public void touch(boolean fromStaff) {
        this.lastFromStaff = fromStaff;
        this.updatedAt = Instant.now();
    }

    public void close(Status status, String by, String resolution, BigDecimal refund) {
        this.status = status;
        this.resolvedBy = by;
        this.resolution = resolution;
        this.refundAmount = refund;
        this.resolvedAt = Instant.now();
        touch(true);
    }
}
