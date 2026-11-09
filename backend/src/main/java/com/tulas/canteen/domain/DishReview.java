package com.tulas.canteen.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * A student's stars (and optional words) for one dish of a collected order. One per order and dish.
 * Stars always count towards the dish rating; the words are shown to others only while {@code VISIBLE}.
 */
@Entity
@Table(name = "dish_reviews",
        uniqueConstraints = @UniqueConstraint(columnNames = {"order_id", "menu_item_id"}),
        indexes = {
                @Index(columnList = "menu_item_id,status,created_at"),
                @Index(columnList = "status,created_at")
        })
public class DishReview {

    /** FLAGGED = held for an admin to check (looks like a link, phone number or abuse). */
    public enum Status { VISIBLE, FLAGGED, HIDDEN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "menu_item_id", nullable = false)
    private Long menuItemId;

    @Column(nullable = false, length = 80)
    private String dishName;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Shown publicly, e.g. "Asha R." */
    @Column(nullable = false, length = 40)
    private String authorLabel;

    @Column(nullable = false)
    private int stars;

    @Column(length = 400)
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.VISIBLE;

    @Column(length = 120)
    private String flagReason;

    @Column(length = 80)
    private String moderatedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    private Instant updatedAt;

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getMenuItemId() { return menuItemId; }
    public void setMenuItemId(Long menuItemId) { this.menuItemId = menuItemId; }
    public String getDishName() { return dishName; }
    public void setDishName(String dishName) { this.dishName = dishName; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getAuthorLabel() { return authorLabel; }
    public void setAuthorLabel(String authorLabel) { this.authorLabel = authorLabel; }
    public int getStars() { return stars; }
    public void setStars(int stars) { this.stars = stars; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getFlagReason() { return flagReason; }
    public void setFlagReason(String flagReason) { this.flagReason = flagReason; }
    public String getModeratedBy() { return moderatedBy; }
    public void setModeratedBy(String moderatedBy) { this.moderatedBy = moderatedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
