package com.tulas.canteen.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/** The canteen's local time zone, used for "today", order numbers and opening hours. */
@Component
public class Clock {

    private final ZoneId zone;

    public Clock(@Value("${app.zone}") String zone) {
        this.zone = ZoneId.of(zone);
    }

    public ZoneId zone() { return zone; }

    public LocalDate today() { return LocalDate.now(zone); }

    public LocalTime now() { return LocalTime.now(zone); }
}
