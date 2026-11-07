package com.tulas.canteen.service;

import com.tulas.canteen.security.AuthUser;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Live updates over Server-Sent Events. Events are small "something changed" signals
 * (e.g. {type: "order", id: 12, status: "READY"}); screens re-fetch what they show.
 * Events are sent only after the database transaction commits, so a screen never
 * re-fetches data that isn't saved yet.
 */
@Component
public class LiveHub {

    private record Client(AuthUser user, SseEmitter emitter) {}

    private final Map<SseEmitter, Client> clients = new ConcurrentHashMap<>();

    public SseEmitter connect(AuthUser user) {
        SseEmitter emitter = new SseEmitter(0L); // no timeout; heartbeats keep proxies from closing it
        clients.put(emitter, new Client(user, emitter));
        emitter.onCompletion(() -> clients.remove(emitter));
        emitter.onTimeout(() -> clients.remove(emitter));
        emitter.onError(e -> clients.remove(emitter));
        send(emitter, "hello", Map.of("ok", true));
        return emitter;
    }

    /** To one user (all their open tabs/phones). */
    public void toUser(Long userId, String event, Object data) {
        publish(c -> c.user().id().equals(userId), event, data);
    }

    /** To kitchen staff and admins. */
    public void toStaff(String event, Object data) {
        publish(c -> c.user().isStaffOrAdmin(), event, data);
    }

    public void toEveryone(String event, Object data) {
        publish(c -> true, event, data);
    }

    private void publish(Predicate<Client> who, String event, Object data) {
        Runnable job = () -> clients.values().stream().filter(who).forEach(c -> send(c.emitter(), event, data));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    job.run();
                }
            });
        } else {
            job.run();
        }
    }

    private void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException | IllegalStateException e) {
            clients.remove(emitter);
        }
    }

    @Scheduled(fixedRate = 25_000)
    void heartbeat() {
        clients.keySet().forEach(e -> {
            try {
                e.send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException ex) {
                clients.remove(e);
            }
        });
    }

    public int connectedCount() {
        return clients.size();
    }
}
