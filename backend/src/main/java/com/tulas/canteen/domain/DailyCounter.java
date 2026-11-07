package com.tulas.canteen.domain;

import jakarta.persistence.*;

import java.time.LocalDate;

/** Hands out order numbers T-001, T-002 ... starting again each day. */
@Entity
@Table(name = "daily_counters")
public class DailyCounter {

    @Id
    @Column(name = "counter_date")
    private LocalDate day;

    @Column(nullable = false)
    private int lastNumber = 0;

    public DailyCounter() {}

    public DailyCounter(LocalDate day) { this.day = day; }

    public LocalDate getDay() { return day; }

    public int next() { return ++lastNumber; }
}
