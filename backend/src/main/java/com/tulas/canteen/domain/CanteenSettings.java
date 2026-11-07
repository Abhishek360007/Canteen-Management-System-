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
