package com.tulas.canteen.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/** Every change to a wallet balance is recorded here, so the balance can always be explained. */
@Entity
@Table(name = "wallet_txns", indexes = @Index(columnList = "user_id,created_at"))
public class WalletTxn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TxnType type;

    /** Positive for money in (recharge, refund), negative for money out (payment). */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal balanceAfter;

    @Column(length = 120)
    private String note;

    /** Name of the admin who recharged, or "system". */
    @Column(length = 80)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public WalletTxn() {}

    public WalletTxn(User user, TxnType type, BigDecimal amount, BigDecimal balanceAfter, String note, String createdBy) {
        this.user = user;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.note = note;
        this.createdBy = createdBy;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public TxnType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public String getNote() { return note; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
