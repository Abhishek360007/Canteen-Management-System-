package com.tulas.canteen.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 120)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role;

    @Column(length = 15)
    private String phone;

    /** College roll number / employee id. */
    @Column(length = 30)
    private String rollNo;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal walletBalance = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean blocked = false;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_favourites", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "menu_item_id")
    private Set<Long> favouriteItemIds = new HashSet<>();

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    /**
     * Goes up when the password changes or an admin resets it. Login tokens carry the version they
     * were issued with, so older tokens (other phones, a stolen token) stop working.
     * Nullable so the column can be added to existing databases; null means 0.
     */
    private Integer tokenVersion;

    public int getTokenVersion() { return tokenVersion == null ? 0 : tokenVersion; }
    public void bumpTokenVersion() { tokenVersion = getTokenVersion() + 1; }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getRollNo() { return rollNo; }
    public void setRollNo(String rollNo) { this.rollNo = rollNo; }
    public BigDecimal getWalletBalance() { return walletBalance; }
    public void setWalletBalance(BigDecimal walletBalance) { this.walletBalance = walletBalance; }
    public boolean isBlocked() { return blocked; }
    public void setBlocked(boolean blocked) { this.blocked = blocked; }
    public Set<Long> getFavouriteItemIds() { return favouriteItemIds; }
    public Instant getCreatedAt() { return createdAt; }
}
