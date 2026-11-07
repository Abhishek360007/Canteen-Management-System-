package com.tulas.canteen.service;

import com.tulas.canteen.config.ApiException;
import com.tulas.canteen.domain.Notification;
import com.tulas.canteen.domain.PushSubscription;
import com.tulas.canteen.domain.Role;
import com.tulas.canteen.repo.NotificationRepository;
import com.tulas.canteen.repo.PushSubscriptionRepository;
import com.tulas.canteen.repo.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One place to tell someone something: saves it for the bell (in-app list), sends a live update to open
 * screens, and a browser push to their subscribed devices.
 */
@Service
public class NotificationService {

    private final NotificationRepository repo;
    private final PushSubscriptionRepository subs;
    private final UserRepository users;
    private final PushSender push;
    private final LiveHub live;
    private final Clock clock;
    /** Low-stock alerts: once per dish per day. */
    private final Map<Long, LocalDate> lowStockSent = new ConcurrentHashMap<>();

    public NotificationService(NotificationRepository repo, PushSubscriptionRepository subs, UserRepository users,
                               PushSender push, LiveHub live, Clock clock) {
        this.repo = repo;
        this.subs = subs;
        this.users = users;
        this.push = push;
        this.live = live;
        this.clock = clock;
    }

    public record View(Long id, String type, String title, String body, String link, boolean read, Instant createdAt) {
        static View of(Notification n) {
            return new View(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getLink(), n.isRead(), n.getCreatedAt());
        }
    }

    @Transactional
    public void notify(Long userId, String type, String title, String body, String link) {
        Notification n = repo.save(new Notification(userId, type, title, body, link));
        live.toUser(userId, "notification", Map.of("id", n.getId()));
        push.sendToUser(userId, Map.of("title", title, "body", body == null ? "" : body,
                "link", link == null ? "/" : link, "tag", type));
    }

    /** To every kitchen staff member and admin. */
    @Transactional
    public void notifyStaff(String type, String title, String body, String link) {
        users.findIdsByRoleIn(List.of(Role.STAFF, Role.ADMIN)).forEach(id -> notify(id, type, title, body, link));
    }

    /** Called after an order reserves plates: warn the kitchen once a day when a dish runs low or sells out. */
    @Transactional
    public void checkLowStock(Long canteenId, Long dishId, String name, Integer left, Integer dailyPlates) {
        if (left == null) return;
        int threshold = Math.max(3, dailyPlates == null ? 0 : (int) Math.ceil(dailyPlates * 0.1));
        if (left > threshold) return;
        LocalDate today = clock.today();
        // Sold out is worth a second alert even if "running low" was sent earlier today.
        Long mapKey = left == 0 ? -dishId : dishId;
        if (today.equals(lowStockSent.put(mapKey, today))) return;
        String title = left == 0 ? name + " is sold out" : name + " is running low";
        String body = left == 0 ? "No plates left today. Make more or switch it off." : "Only " + left + " plate(s) left.";
        users.findAdminAndStaffIdsOf(canteenId).forEach(id -> notify(id, "LOW_STOCK", title, body, "/kitchen/stock"));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list(Long userId) {
        return Map.of("items", repo.findTop50ByUserIdOrderByCreatedAtDesc(userId).stream().map(View::of).toList(),
                "unread", repo.countByUserIdAndReadFalse(userId));
    }

    @Transactional(readOnly = true)
    public long unread(Long userId) {
        return repo.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markAllRead(Long userId) {
        repo.markAllRead(userId);
    }

    // ---- push subscriptions

    @Transactional
    public void subscribe(Long userId, String endpoint, String p256dh, String auth) {
        PushSender.checkEndpoint(endpoint);
        if (p256dh == null || p256dh.isBlank() || auth == null || auth.isBlank()) {
            throw ApiException.badRequest("Invalid push subscription.");
        }
        PushSubscription s = subs.findByEndpoint(endpoint).orElseGet(() -> new PushSubscription(userId, endpoint, p256dh, auth));
        // Same browser, different user logged in: the subscription follows the current user.
        s.setUserId(userId);
        s.setP256dh(p256dh);
        s.setAuth(auth);
        subs.save(s);
    }

    @Transactional
    public void unsubscribe(Long userId, String endpoint) {
        subs.findByEndpoint(endpoint).filter(s -> s.getUserId().equals(userId)).ifPresent(subs::delete);
    }

    @Transactional(readOnly = true)
    public int deviceCount(Long userId) {
        return subs.findByUserId(userId).size();
    }
}
