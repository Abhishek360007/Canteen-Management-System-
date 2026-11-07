package com.tulas.canteen.domain;

import jakarta.persistence.*;

import java.time.Instant;

/** An in-app notification (the bell). The same message is also sent as a browser push when possible. */
@Entity
@Table(name = "notifications", indexes = @Index(columnList = "user_id,created_at"))
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** ORDER_READY, REFUND, PAYMENT, TOPUP, WALLET, POINTS, LOW_STOCK … */
    @Column(nullable = false, length = 20)
    private String type;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 240)
    private String body;

    /** In-app path to open, e.g. /orders/12. */
    @Column(length = 120)
    private String link;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Notification() {}

    public Notification(Long userId, String type, String title, String body, String link) {
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.body = body;
        this.link = link;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getLink() { return link; }
    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
    public Instant getCreatedAt() { return createdAt; }
}
