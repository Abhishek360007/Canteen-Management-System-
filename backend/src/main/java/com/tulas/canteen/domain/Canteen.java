package com.tulas.canteen.domain;

import jakarta.persistence.*;

import java.time.LocalTime;

/** One canteen / food outlet on campus, with its own menu, stock, kitchen staff, hours and rules. */
@Entity
@Table(name = "canteens", uniqueConstraints = @UniqueConstraint(columnNames = "code"))
public class Canteen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String name;

    /** Short letter(s) used in order numbers, e.g. "T" → T-017. Unique across canteens. */
    @Column(nullable = false, length = 3)
    private String code;

    /** Where students pick up, e.g. "Ground floor, Main block". */
    @Column(length = 120)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private CanteenStatus status = CanteenStatus.OPEN;

    @Column(nullable = false)
    private LocalTime openTime = LocalTime.of(8, 0);

    @Column(nullable = false)
    private LocalTime closeTime = LocalTime.of(18, 0);

    @Column(nullable = false)
    private int maxActiveOrders = 3;

    @Column(nullable = false)
    private int maxQtyPerItem = 6;

    @Column(nullable = false)
    private int noShowMinutes = 45;

    @Column(nullable = false)
    private boolean cashAllowed = true;

    /** Hidden from students (e.g. closed for renovation) without deleting its history. */
    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private int sortOrder = 0;

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public CanteenStatus getStatus() { return status; }
    public void setStatus(CanteenStatus status) { this.status = status; }
    public LocalTime getOpenTime() { return openTime; }
    public void setOpenTime(LocalTime openTime) { this.openTime = openTime; }
    public LocalTime getCloseTime() { return closeTime; }
    public void setCloseTime(LocalTime closeTime) { this.closeTime = closeTime; }
    public int getMaxActiveOrders() { return maxActiveOrders; }
    public void setMaxActiveOrders(int v) { this.maxActiveOrders = v; }
    public int getMaxQtyPerItem() { return maxQtyPerItem; }
    public void setMaxQtyPerItem(int v) { this.maxQtyPerItem = v; }
    public int getNoShowMinutes() { return noShowMinutes; }
    public void setNoShowMinutes(int v) { this.noShowMinutes = v; }
    public boolean isCashAllowed() { return cashAllowed; }
    public void setCashAllowed(boolean v) { this.cashAllowed = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
