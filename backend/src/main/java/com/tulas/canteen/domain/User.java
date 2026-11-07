package com.tulas.canteen.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * {@code @DynamicUpdate}: saving a change (e.g. blocking a user) writes only the changed columns, so it can
 * never overwrite a wallet balance or points total that an atomic UPDATE changed in the meantime.
 */
@Entity
@org.hibernate.annotations.DynamicUpdate
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

    /** Kitchen staff work at one canteen. Null for students and admins. */
    @Column(name = "canteen_id")
    private Long canteenId;

    public Long getCanteenId() { return canteenId; }
    public void setCanteenId(Long canteenId) { this.canteenId = canteenId; }

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

    // ---- rewards (all nullable so the columns can be added to an existing database)

    /** Reward points balance. Changed only through atomic updates in UserRepository. */
    private Integer loyaltyPoints;

    /** Total paid for collected orders; decides the reward tier. */
    @Column(precision = 12, scale = 2)
    private BigDecimal lifetimeSpend;

    /** Optional; can be set once (stops changing it every day for bonus points). */
    private java.time.LocalDate birthday;

    /** Year the last birthday bonus was given, so it's given once a year. */
    private Integer birthdayBonusYear;

    /** This student's code to invite friends, e.g. "ASHA7K2". */
    @Column(length = 12, unique = true)
    private String referralCode;

    /** Who invited this student (rewarded when this student's first order is collected). */
    private Long referredById;

    private Boolean referralRewarded;

    public int getLoyaltyPoints() { return loyaltyPoints == null ? 0 : loyaltyPoints; }
    public BigDecimal getLifetimeSpend() { return lifetimeSpend == null ? BigDecimal.ZERO : lifetimeSpend; }
    public java.time.LocalDate getBirthday() { return birthday; }
    public void setBirthday(java.time.LocalDate birthday) { this.birthday = birthday; }
    public Integer getBirthdayBonusYear() { return birthdayBonusYear; }
    public void setBirthdayBonusYear(Integer year) { this.birthdayBonusYear = year; }
    public String getReferralCode() { return referralCode; }
    public void setReferralCode(String referralCode) { this.referralCode = referralCode; }
    public Long getReferredById() { return referredById; }
    public void setReferredById(Long referredById) { this.referredById = referredById; }
    public boolean isReferralRewarded() { return Boolean.TRUE.equals(referralRewarded); }
    public void setReferralRewarded(boolean v) { this.referralRewarded = v; }

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
