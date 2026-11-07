package com.tulas.canteen.domain;

import jakarta.persistence.*;

import java.time.Instant;

/** A browser that agreed to receive push notifications for a user (one per device/browser). */
@Entity
@Table(name = "push_subscriptions", indexes = @Index(columnList = "user_id"))
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true, length = 600)
    private String endpoint;

    @Column(nullable = false, length = 120)
    private String p256dh;

    @Column(nullable = false, length = 60)
    private String auth;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public PushSubscription() {}

    public PushSubscription(Long userId, String endpoint, String p256dh, String auth) {
        this.userId = userId;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getEndpoint() { return endpoint; }
    public String getP256dh() { return p256dh; }
    public void setP256dh(String p256dh) { this.p256dh = p256dh; }
    public String getAuth() { return auth; }
    public void setAuth(String auth) { this.auth = auth; }
}
