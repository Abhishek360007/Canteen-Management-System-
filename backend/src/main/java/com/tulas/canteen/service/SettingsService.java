package com.tulas.canteen.service;

import com.tulas.canteen.domain.CanteenSettings;
import com.tulas.canteen.domain.CanteenStatus;
import com.tulas.canteen.dto.AdminDtos.CanteenView;
import com.tulas.canteen.dto.AdminDtos.SettingsRequest;
import com.tulas.canteen.repo.SettingsRepository;
import com.tulas.canteen.config.ApiException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalTime;
import java.util.Map;

@Service
public class SettingsService implements SmartInitializingSingleton {

    private final SettingsRepository repo;
    private final Clock clock;
    private final LiveHub live;
    private final TransactionTemplate tx;

    public SettingsService(SettingsRepository repo, Clock clock, LiveHub live, PlatformTransactionManager txm) {
        this.repo = repo;
        this.clock = clock;
        this.live = live;
        this.tx = new TransactionTemplate(txm);
    }

    /**
     * Creates the settings row once at start-up, before the web server accepts requests,
     * so concurrent first requests never race to insert it.
     */
    @Override
    public void afterSingletonsInstantiated() {
        tx.executeWithoutResult(s -> {
            if (!repo.existsById(CanteenSettings.SINGLETON_ID)) repo.save(new CanteenSettings());
        });
    }

    @Transactional(readOnly = true)
    public CanteenSettings get() {
        return repo.findById(CanteenSettings.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("canteen_settings row is missing"));
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
        s.setUpiId(r.upiId() == null || r.upiId().isBlank() ? null : r.upiId().trim());
        s.setUpiName(r.upiName() == null || r.upiName().isBlank() ? null : r.upiName().trim());
        live.toEveryone("canteen", Map.of("status", s.getStatus()));
        return CanteenView.of(s, isOpenNow(s));
    }
}
