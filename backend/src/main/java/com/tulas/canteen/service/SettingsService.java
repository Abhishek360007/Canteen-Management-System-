package com.tulas.canteen.service;

import com.tulas.canteen.domain.CanteenSettings;
import com.tulas.canteen.domain.CanteenStatus;
import com.tulas.canteen.dto.AdminDtos.CanteenView;
import com.tulas.canteen.dto.AdminDtos.SettingsRequest;
import com.tulas.canteen.repo.SettingsRepository;
import com.tulas.canteen.config.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.Map;

@Service
public class SettingsService {

    private final SettingsRepository repo;
    private final Clock clock;
    private final LiveHub live;

    public SettingsService(SettingsRepository repo, Clock clock, LiveHub live) {
        this.repo = repo;
        this.clock = clock;
        this.live = live;
    }

    @Transactional
    public CanteenSettings get() {
        return repo.findById(CanteenSettings.SINGLETON_ID).orElseGet(() -> repo.save(new CanteenSettings()));
    }

    /** Taking orders right now: switched on by staff AND inside opening hours. */
    public boolean isOpenNow(CanteenSettings s) {
        if (s.getStatus() == CanteenStatus.CLOSED) return false;
        LocalTime now = clock.now();
        return !now.isBefore(s.getOpenTime()) && now.isBefore(s.getCloseTime());
    }

    @Transactional
    public CanteenView view() {
        CanteenSettings s = get();
        return CanteenView.of(s, isOpenNow(s));
    }

    @Transactional
    public CanteenView setStatus(CanteenStatus status) {
        CanteenSettings s = get();
        s.setStatus(status);
        live.toEveryone("canteen", Map.of("status", status));
        return CanteenView.of(s, isOpenNow(s));
    }

    @Transactional
    public CanteenView update(SettingsRequest r) {
        if (!r.closeTime().isAfter(r.openTime())) {
            throw ApiException.badRequest("Closing time must be after opening time.");
        }
        CanteenSettings s = get();
        s.setOpenTime(r.openTime());
        s.setCloseTime(r.closeTime());
        s.setMaxActiveOrders(r.maxActiveOrders());
        s.setMaxQtyPerItem(r.maxQtyPerItem());
        s.setNoShowMinutes(r.noShowMinutes());
        s.setCashAllowed(r.cashAllowed());
        live.toEveryone("canteen", Map.of("status", s.getStatus()));
        return CanteenView.of(s, isOpenNow(s));
    }
}
