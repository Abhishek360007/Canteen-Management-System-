package com.tulas.canteen.web;

import com.tulas.canteen.security.AuthUser;
import com.tulas.canteen.service.NotificationService;
import com.tulas.canteen.service.PushSender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** The bell (in-app notifications) and browser push subscriptions. For every logged-in user. */
@RestController
@RequestMapping("/api")
public class NotificationController {

    private final NotificationService notifications;
    private final PushSender push;

    public NotificationController(NotificationService notifications, PushSender push) {
        this.notifications = notifications;
        this.push = push;
    }

    @GetMapping("/notifications")
    public Map<String, Object> list(@AuthenticationPrincipal AuthUser me) {
        return notifications.list(me.id());
    }

    @GetMapping("/notifications/unread")
    public Map<String, Long> unread(@AuthenticationPrincipal AuthUser me) {
        return Map.of("unread", notifications.unread(me.id()));
    }

    @PostMapping("/notifications/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAll(@AuthenticationPrincipal AuthUser me) {
        notifications.markAllRead(me.id());
    }

    // ---- browser push

    @GetMapping("/push/key")
    public Map<String, Object> key(@AuthenticationPrincipal AuthUser me) {
        return Map.of("publicKey", push.publicKey(), "devices", notifications.deviceCount(me.id()));
    }

    public record Keys(@NotBlank @Size(max = 120) String p256dh, @NotBlank @Size(max = 60) String auth) {}

    public record Subscription(@NotBlank @Size(max = 600) String endpoint, @NotNull @Valid Keys keys) {}

    @PostMapping("/push/subscribe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void subscribe(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody Subscription s) {
        notifications.subscribe(me.id(), s.endpoint(), s.keys().p256dh(), s.keys().auth());
    }

    public record Endpoint(@NotBlank @Size(max = 600) String endpoint) {}

    @PostMapping("/push/unsubscribe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody Endpoint e) {
        notifications.unsubscribe(me.id(), e.endpoint());
    }

    /** Lets a user check that alerts reach their phone. */
    @PostMapping("/push/test")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void test(@AuthenticationPrincipal AuthUser me) {
        notifications.notify(me.id(), "TEST", "Alerts are working ✅",
                "You'll get a notification like this when your food is ready.", "/");
    }
}
