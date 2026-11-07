package com.tulas.canteen.service;

import com.tulas.canteen.domain.CanteenSettings;
import com.tulas.canteen.dto.AdminDtos.SettingsRequest;
import com.tulas.canteen.dto.AdminDtos.SettingsView;
import com.tulas.canteen.repo.SettingsRepository;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

/**
 * Campus-wide settings (single row, id 1): the UPI details for wallet top-ups, since one wallet works at every
 * canteen. Per-canteen hours, status and rules live on {@link com.tulas.canteen.domain.Canteen}; the old
 * per-canteen columns in this row are only read once, to set up the first canteen.
 */
@Service
public class SettingsService implements SmartInitializingSingleton {

    private final SettingsRepository repo;
    private final LiveHub live;
    private final TransactionTemplate tx;

    public SettingsService(SettingsRepository repo, LiveHub live, PlatformTransactionManager txm) {
        this.repo = repo;
        this.live = live;
        this.tx = new TransactionTemplate(txm);
    }

    /** Creates the row once at start-up, before the web server accepts requests. */
    @Override
    public void afterSingletonsInstantiated() {
        tx.executeWithoutResult(s -> {
            if (!repo.existsById(CanteenSettings.SINGLETON_ID)) repo.save(new CanteenSettings());
        });
    }

    @Transactional(readOnly = true)
    public CanteenSettings get() {
        return repo.findById(CanteenSettings.SINGLETON_ID).orElseGet(CanteenSettings::new);
    }

    @Transactional(readOnly = true)
    public SettingsView view() {
        CanteenSettings s = get();
        return new SettingsView(s.getUpiId(), s.getUpiName());
    }

    @Transactional
    public SettingsView update(SettingsRequest r) {
        CanteenSettings s = repo.findById(CanteenSettings.SINGLETON_ID).orElseGet(() -> repo.save(new CanteenSettings()));
        s.setUpiId(r.upiId() == null || r.upiId().isBlank() ? null : r.upiId().trim());
        s.setUpiName(r.upiName() == null || r.upiName().isBlank() ? null : r.upiName().trim());
        live.toEveryone("canteen", Map.of("settings", true));
        return new SettingsView(s.getUpiId(), s.getUpiName());
    }
}
