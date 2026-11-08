package com.tulas.canteen.domain;

import jakarta.persistence.*;

import java.time.LocalTime;

/** Single-row table (id = 1) with the canteen's live status and order rules. */
@Entity
@Table(name = "canteen_settings")
public class CanteenSettings {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private CanteenStatus status = CanteenStatus.OPEN;

    @Column(nullable = false)
    private LocalTime openTime = LocalTime.of(8, 0);

    @Column(nullable = false)
    private LocalTime closeTime = LocalTime.of(18, 0);

    /** Most orders one student can have in progress at once. */
    @Column(nullable = false)
    private int maxActiveOrders = 3;

    /** Most plates of one dish in a single order. */
    @Column(nullable = false)
    private int maxQtyPerItem = 6;

    /** A READY order not collected within this many minutes becomes a no-show. */
    @Column(nullable = false)
    private int noShowMinutes = 45;

    @Column(nullable = false)
    private boolean cashAllowed = true;

    /** Canteen's UPI ID (e.g. tulascanteen@okaxis). Empty = wallet top-up requests switched off. */
    @Column(length = 60)
    private String upiId;

    /** Name shown in the student's UPI app when paying. */
    @Column(length = 60)
    private String upiName;

    // ---- online payment (Razorpay); nullable so the columns can be added to an existing database

    /** Admin switch for online payment (it also needs Razorpay keys on the server). null = on. */
    private Boolean onlinePaymentsEnabled;

    /** Smallest / largest wallet top-up in rupees. null = 50 / 5000. */
    private Integer minTopup;
    private Integer maxTopup;

    public boolean isOnlinePaymentsEnabled() { return onlinePaymentsEnabled == null || onlinePaymentsEnabled; }
    public void setOnlinePaymentsEnabled(boolean v) { this.onlinePaymentsEnabled = v; }
    public int getMinTopup() { return minTopup == null ? 50 : minTopup; }
    public void setMinTopup(int v) { this.minTopup = v; }
    public int getMaxTopup() { return maxTopup == null ? 5000 : maxTopup; }
    public void setMaxTopup(int v) { this.maxTopup = v; }

    public String getUpiId() { return upiId; }
    public void setUpiId(String upiId) { this.upiId = upiId; }
    public String getUpiName() { return upiName; }
    public void setUpiName(String upiName) { this.upiName = upiName; }

    public Long getId() { return id; }
    public CanteenStatus getStatus() { return status; }
    public void setStatus(CanteenStatus status) { this.status = status; }
    public LocalTime getOpenTime() { return openTime; }
    public void setOpenTime(LocalTime openTime) { this.openTime = openTime; }
    public LocalTime getCloseTime() { return closeTime; }
    public void setCloseTime(LocalTime closeTime) { this.closeTime = closeTime; }
    public int getMaxActiveOrders() { return maxActiveOrders; }
    public void setMaxActiveOrders(int maxActiveOrders) { this.maxActiveOrders = maxActiveOrders; }
    public int getMaxQtyPerItem() { return maxQtyPerItem; }
    public void setMaxQtyPerItem(int maxQtyPerItem) { this.maxQtyPerItem = maxQtyPerItem; }
    public int getNoShowMinutes() { return noShowMinutes; }
    public void setNoShowMinutes(int noShowMinutes) { this.noShowMinutes = noShowMinutes; }
    public boolean isCashAllowed() { return cashAllowed; }
    public void setCashAllowed(boolean cashAllowed) { this.cashAllowed = cashAllowed; }
}
