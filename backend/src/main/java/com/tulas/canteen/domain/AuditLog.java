package com.tulas.canteen.domain;

import jakarta.persistence.*;

import java.time.Instant;

/** Who did what, when: money, user and menu actions by admins and kitchen staff. */
@Entity
@Table(name = "audit_log", indexes = @Index(columnList = "created_at"))
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long actorId;

    @Column(nullable = false, length = 80)
    private String actorName;

    /** Short machine-friendly action, e.g. WALLET_RECHARGE, USER_BLOCKED, DISH_UPDATED. */
    @Column(nullable = false, length = 40)
    private String action;

    /** What it was done to, e.g. "Asha Rao (asha@college.edu)" or "Order T-017". */
    @Column(length = 160)
    private String target;

    @Column(length = 300)
    private String details;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public AuditLog() {}

    public AuditLog(Long actorId, String actorName, String action, String target, String details) {
        this.actorId = actorId;
        this.actorName = actorName;
        this.action = action;
        this.target = trim(target, 160);
        this.details = trim(details, 300);
    }

    private static String trim(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    public Long getId() { return id; }
    public Long getActorId() { return actorId; }
    public String getActorName() { return actorName; }
    public String getAction() { return action; }
    public String getTarget() { return target; }
    public String getDetails() { return details; }
    public Instant getCreatedAt() { return createdAt; }
}
