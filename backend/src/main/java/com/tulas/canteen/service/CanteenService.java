package com.tulas.canteen.service;

import com.tulas.canteen.config.ApiException;
import com.tulas.canteen.domain.Canteen;
import com.tulas.canteen.domain.CanteenSettings;
import com.tulas.canteen.domain.CanteenStatus;
import com.tulas.canteen.domain.Role;
import com.tulas.canteen.dto.AdminDtos.CanteenRequest;
import com.tulas.canteen.dto.AdminDtos.CanteenView;
import com.tulas.canteen.repo.CanteenRepository;
import com.tulas.canteen.repo.SettingsRepository;
import com.tulas.canteen.security.AuthUser;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * Canteens on campus. Each has its own menu, stock, kitchen staff, hours and rules.
 * Wallet, rewards and UPI top-ups stay campus-wide (see {@link SettingsService}).
 */
@Service
public class CanteenService implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(CanteenService.class);

    private final CanteenRepository canteens;
    private final SettingsRepository legacySettings;
    private final SettingsService settings;
    private final EntityManager em;
    private final TransactionTemplate tx;
    private final LiveHub live;
    private final Clock clock;

    public CanteenService(CanteenRepository canteens, SettingsRepository legacySettings, SettingsService settings,
                          EntityManager em, PlatformTransactionManager txm, LiveHub live, Clock clock) {
        this.canteens = canteens;
        this.legacySettings = legacySettings;
        this.settings = settings;
        this.em = em;
        this.tx = new TransactionTemplate(txm);
        this.live = live;
        this.clock = clock;
    }

    /**
     * Start-up, before requests are served: make sure there is at least one canteen, and move data from the
     * single-canteen version (menu, orders, kitchen staff without a canteen) into the first one.
     */
    @Override
    public void afterSingletonsInstantiated() {
        tx.executeWithoutResult(s -> {
            List<Canteen> all = canteens.findAllByOrderBySortOrderAscIdAsc();
            Canteen main;
            if (all.isEmpty()) {
                main = new Canteen();
                main.setName("Tula's Canteen");
                main.setCode("T");
                main.setLocation("Main canteen");
                legacySettings.findById(CanteenSettings.SINGLETON_ID).ifPresent(old -> {
                    main.setStatus(old.getStatus());
                    main.setOpenTime(old.getOpenTime());
                    main.setCloseTime(old.getCloseTime());
                    main.setMaxActiveOrders(old.getMaxActiveOrders());
                    main.setMaxQtyPerItem(old.getMaxQtyPerItem());
                    main.setNoShowMinutes(old.getNoShowMinutes());
                    main.setCashAllowed(old.isCashAllowed());
                });
                canteens.save(main);
                log.info("Created the main canteen (id {})", main.getId());
            } else {
                main = all.get(0);
            }
            int dishes = em.createQuery("update MenuItem m set m.canteenId = :id where m.canteenId is null")
                    .setParameter("id", main.getId()).executeUpdate();
            int orders = em.createQuery("update Order o set o.canteen = :c where o.canteen is null")
                    .setParameter("c", main).executeUpdate();
            int staff = em.createQuery("update User u set u.canteenId = :id where u.canteenId is null and u.role = :role")
                    .setParameter("id", main.getId()).setParameter("role", Role.STAFF).executeUpdate();
            if (dishes + orders + staff > 0) {
                log.info("Moved {} dish(es), {} order(s) and {} staff to canteen '{}'", dishes, orders, staff, main.getName());
            }
        });
    }

    // ---------------------------------------------------------------- lookup

    @Transactional(readOnly = true)
    public Canteen get(Long id) {
        return canteens.findById(id).orElseThrow(() -> ApiException.notFound("Canteen not found"));
    }

    /** The first active canteen: used when a request doesn't say which one. */
    @Transactional(readOnly = true)
    public Canteen defaultCanteen() {
        return canteens.findByActiveTrueOrderBySortOrderAscIdAsc().stream().findFirst()
                .or(() -> canteens.findAllByOrderBySortOrderAscIdAsc().stream().findFirst())
                .orElseThrow(() -> new IllegalStateException("No canteen configured"));
    }

    /**
     * Which canteen a request is about. Kitchen staff are always pinned to their own canteen (whatever they ask
     * for); students and admins choose with ?canteenId=, defaulting to the first canteen.
     */
    @Transactional(readOnly = true)
    public Canteen resolve(AuthUser me, Long requested) {
        if (me != null && me.role() == Role.STAFF) {
            return me.canteenId() == null ? defaultCanteen() : get(me.canteenId());
        }
        return requested == null ? defaultCanteen() : get(requested);
    }

    /** For admin views that can show "all canteens": null means all. Staff still only see theirs. */
    public Long scope(AuthUser me, Long requested) {
        if (me != null && me.role() == Role.STAFF) return me.canteenId();
        return requested;
    }

    /** Staff can only act on their own canteen's orders and dishes. */
    public void checkAccess(AuthUser me, Long canteenId) {
        if (me != null && me.role() == Role.STAFF && me.canteenId() != null && !me.canteenId().equals(canteenId)) {
            throw ApiException.forbidden("That belongs to another canteen.");
        }
    }

    /** Taking orders right now: switched on by staff AND inside opening hours. */
    public boolean isOpenNow(Canteen c) {
        if (c.getStatus() == CanteenStatus.CLOSED || !c.isActive()) return false;
        LocalTime now = clock.now();
        return !now.isBefore(c.getOpenTime()) && now.isBefore(c.getCloseTime());
    }

    @Transactional(readOnly = true)
    public CanteenView view(Canteen c) {
        CanteenSettings campus = settings.get();
        return CanteenView.of(c, isOpenNow(c), campus.getUpiId(), campus.getUpiName());
    }

    @Transactional(readOnly = true)
    public List<CanteenView> list(boolean includeInactive) {
        CanteenSettings campus = settings.get();
        return (includeInactive ? canteens.findAllByOrderBySortOrderAscIdAsc() : canteens.findByActiveTrueOrderBySortOrderAscIdAsc())
                .stream().map(c -> CanteenView.of(c, isOpenNow(c), campus.getUpiId(), campus.getUpiName())).toList();
    }

    // ---------------------------------------------------------------- changes

    @Transactional
    public CanteenView setStatus(Long id, CanteenStatus status) {
        Canteen c = get(id);
        c.setStatus(status);
        live.toEveryone("canteen", Map.of("id", c.getId(), "status", status));
        return view(c);
    }

    @Transactional
    public CanteenView create(CanteenRequest r) {
        if (canteens.existsByCodeIgnoreCase(r.code().trim())) throw ApiException.conflict("Another canteen already uses code " + r.code() + ".");
        Canteen c = new Canteen();
        c.setSortOrder(canteens.findAll().size());
        apply(c, r);
        canteens.save(c);
        live.toEveryone("canteen", Map.of("id", c.getId()));
        return view(c);
    }

    @Transactional
    public CanteenView update(Long id, CanteenRequest r) {
        Canteen c = get(id);
        if (!c.getCode().equalsIgnoreCase(r.code().trim()) && canteens.existsByCodeIgnoreCase(r.code().trim())) {
            throw ApiException.conflict("Another canteen already uses code " + r.code() + ".");
        }
        if (!r.active() && c.isActive() && canteens.findByActiveTrueOrderBySortOrderAscIdAsc().size() == 1) {
            throw ApiException.badRequest("At least one canteen must stay active.");
        }
        apply(c, r);
        live.toEveryone("canteen", Map.of("id", c.getId()));
        return view(c);
    }

    private void apply(Canteen c, CanteenRequest r) {
        if (!r.closeTime().isAfter(r.openTime())) throw ApiException.badRequest("Closing time must be after opening time.");
        c.setName(r.name().trim());
        c.setCode(r.code().trim().toUpperCase());
        c.setLocation(r.location() == null || r.location().isBlank() ? null : r.location().trim());
        c.setOpenTime(r.openTime());
        c.setCloseTime(r.closeTime());
        c.setMaxActiveOrders(r.maxActiveOrders());
        c.setMaxQtyPerItem(r.maxQtyPerItem());
        c.setNoShowMinutes(r.noShowMinutes());
        c.setCashAllowed(r.cashAllowed());
        c.setActive(r.active());
    }
}
