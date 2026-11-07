package com.tulas.canteen.domain;

import jakarta.persistence.*;

/** Order numbers per canteen per day: key "2026-11-08#3" → T-001, T-002 … (replaces the single-canteen DailyCounter). */
@Entity
@Table(name = "order_counters")
public class OrderCounter {

    @Id
    @Column(name = "counter_key", length = 32)
    private String key;

    @Column(nullable = false)
    private int lastNumber = 0;

    public OrderCounter() {}

    public OrderCounter(String key) { this.key = key; }

    /** Start after numbers already used today. */
    public OrderCounter(String key, int lastNumber) {
        this.key = key;
        this.lastNumber = lastNumber;
    }

    public static String key(java.time.LocalDate day, Long canteenId) {
        return day + "#" + canteenId;
    }

    public String getKey() { return key; }

    public int next() { return ++lastNumber; }
}
