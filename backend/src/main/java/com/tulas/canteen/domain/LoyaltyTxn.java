package com.tulas.canteen.domain;

import jakarta.persistence.*;

import java.time.Instant;

/** Every change to a student's reward points, so the balance can always be explained. */
@Entity
@Table(name = "loyalty_txns", indexes = @Index(columnList = "user_id,created_at"))
public class LoyaltyTxn {

    public enum Type { EARN, REDEEM, RESTORE, BIRTHDAY, REFERRAL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Type type;

    /** Positive = points in, negative = points out. */
    @Column(nullable = false)
    private int points;

    @Column(nullable = false)
    private int balanceAfter;

    @Column(length = 120)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public LoyaltyTxn() {}

    public LoyaltyTxn(User user, Type type, int points, int balanceAfter, String note) {
        this.user = user;
        this.type = type;
        this.points = points;
        this.balanceAfter = balanceAfter;
        this.note = note;
    }

    public Long getId() { return id; }
    public Type getType() { return type; }
    public int getPoints() { return points; }
    public int getBalanceAfter() { return balanceAfter; }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }
}
