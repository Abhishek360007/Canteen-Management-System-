package com.tulas.canteen.security;

import com.tulas.canteen.config.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Brute-force protection, kept in memory (fine for a single server):
 * <ul>
 *   <li>5 wrong passwords for one email within 15 minutes locks that email for 15 minutes;</li>
 *   <li>30 wrong passwords from one IP within 15 minutes locks that IP (stops guessing across many emails);</li>
 *   <li>sign-ups are limited per IP per hour (stops fake-account spam).</li>
 * </ul>
 */
@Component
public class LoginGuard {

    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int MAX_PER_EMAIL = 5;
    private static final int MAX_PER_IP = 30;

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
    private final Map<String, Deque<Instant>> signups = new ConcurrentHashMap<>();
    private final int signupsPerHour;

    public LoginGuard(@Value("${app.limits.signups-per-hour:10}") int signupsPerHour) {
        this.signupsPerHour = signupsPerHour;
    }

    public void checkLogin(String email, String ip) {
        check(failures, "email:" + email.toLowerCase(), MAX_PER_EMAIL, WINDOW,
                "Too many wrong passwords for this account. Try again in %d minutes.");
        check(failures, "ip:" + ip, MAX_PER_IP, WINDOW,
                "Too many failed logins from your network. Try again in %d minutes.");
    }

    public void loginFailed(String email, String ip) {
        add(failures, "email:" + email.toLowerCase());
        add(failures, "ip:" + ip);
    }

    public void loginSucceeded(String email) {
        failures.remove("email:" + email.toLowerCase());
    }

    public void checkSignup(String ip) {
        check(signups, ip, signupsPerHour, Duration.ofHours(1),
                "Too many new accounts from your network. Try again in %d minutes.");
        add(signups, ip);
    }

    private static void check(Map<String, Deque<Instant>> map, String key, int max, Duration window, String msg) {
        Deque<Instant> q = map.get(key);
        if (q == null) return;
        synchronized (q) {
            prune(q, window);
            if (q.size() >= max) {
                long mins = Math.max(1, Duration.between(Instant.now(), q.peekFirst().plus(window)).toMinutes() + 1);
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, String.format(msg, mins));
            }
        }
    }

    private static void add(Map<String, Deque<Instant>> map, String key) {
        Deque<Instant> q = map.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            q.addLast(Instant.now());
        }
    }

    private static void prune(Deque<Instant> q, Duration window) {
        Instant cutoff = Instant.now().minus(window);
        while (!q.isEmpty() && q.peekFirst().isBefore(cutoff)) q.pollFirst();
    }

    /** Forget old entries so memory doesn't grow forever. */
    @Scheduled(fixedDelay = 600_000)
    void cleanup() {
        failures.entrySet().removeIf(e -> { synchronized (e.getValue()) { prune(e.getValue(), WINDOW); return e.getValue().isEmpty(); } });
        signups.entrySet().removeIf(e -> { synchronized (e.getValue()) { prune(e.getValue(), Duration.ofHours(1)); return e.getValue().isEmpty(); } });
    }

    /**
     * Client IP behind a hosting proxy. Proxy headers can be forged by the client, which is why the
     * per-email limit (not the IP limit) is the main protection.
     */
    public static String clientIp(jakarta.servlet.http.HttpServletRequest req) {
        String trueIp = req.getHeader("True-Client-IP");
        if (trueIp != null && !trueIp.isBlank()) return trueIp.trim();
        String fwd = req.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) return fwd.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
