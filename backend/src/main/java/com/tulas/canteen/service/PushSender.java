package com.tulas.canteen.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tulas.canteen.config.ApiException;
import com.tulas.canteen.domain.AppSecret;
import com.tulas.canteen.domain.PushSubscription;
import com.tulas.canteen.repo.AppSecretRepository;
import com.tulas.canteen.repo.PushSubscriptionRepository;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigInteger;
import java.net.URI;
import java.security.*;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Browser push notifications (Web Push: RFC 8030 delivery, RFC 8291 encryption, RFC 8292 VAPID).
 * <ul>
 *   <li>VAPID keys come from VAPID_PUBLIC_KEY / VAPID_PRIVATE_KEY, or are generated once and stored in the
 *       database, so existing browser subscriptions keep working after a restart.</li>
 *   <li>Messages are only sent to the browsers' official push services (allow-list), so a crafted
 *       subscription can't make the server call arbitrary addresses (SSRF).</li>
 *   <li>Sending happens in the background after the database change commits; subscriptions the push
 *       service says are gone (404/410) are deleted.</li>
 * </ul>
 */
@Service
public class PushSender implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(PushSender.class);
    private static final List<String> ALLOWED_HOST_SUFFIXES = List.of(
            "fcm.googleapis.com", "push.services.mozilla.com", "notify.windows.com", "push.apple.com", "web.push.apple.com");

    private final AppSecretRepository secrets;
    private final PushSubscriptionRepository subs;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    private final String configuredPublic;
    private final String configuredPrivate;
    private final String subject;
    private final ExecutorService pool = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "web-push");
        t.setDaemon(true);
        return t;
    });

    private volatile PushService push;
    private volatile String publicKey;

    public PushSender(AppSecretRepository secrets, PushSubscriptionRepository subs, PlatformTransactionManager txm,
                      ObjectMapper json,
                      @Value("${app.push.public-key:}") String configuredPublic,
                      @Value("${app.push.private-key:}") String configuredPrivate,
                      @Value("${app.push.subject:mailto:admin@tulas.local}") String subject) {
        this.secrets = secrets;
        this.subs = subs;
        this.tx = new TransactionTemplate(txm);
        this.json = json;
        this.configuredPublic = configuredPublic;
        this.configuredPrivate = configuredPrivate;
        this.subject = subject;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) Security.addProvider(new BouncyCastleProvider());
        try {
            String pub = configuredPublic, priv = configuredPrivate;
            if (pub.isBlank() || priv.isBlank()) {
                String[] keys = tx.execute(s -> {
                    var p = secrets.findById("vapid.public");
                    var k = secrets.findById("vapid.private");
                    if (p.isPresent() && k.isPresent()) return new String[]{p.get().getValue(), k.get().getValue()};
                    String[] gen;
                    try {
                        gen = generateKeys();
                    } catch (GeneralSecurityException e) {
                        throw new IllegalStateException(e);
                    }
                    secrets.save(new AppSecret("vapid.public", gen[0]));
                    secrets.save(new AppSecret("vapid.private", gen[1]));
                    log.info("Generated Web Push (VAPID) keys and saved them in the database");
                    return gen;
                });
                pub = keys[0];
                priv = keys[1];
            }
            this.push = new PushService(pub, priv, subject);
            this.publicKey = pub;
        } catch (Exception e) {
            log.warn("Browser push notifications are off: {}", e.getMessage());
        }
    }

    /** The key browsers need to subscribe. */
    public String publicKey() {
        if (publicKey == null) throw ApiException.badRequest("Push notifications aren't available on this server.");
        return publicKey;
    }

    /** Rejects endpoints that aren't an official browser push service. */
    public static void checkEndpoint(String endpoint) {
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Invalid push endpoint.");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        boolean ok = "https".equals(uri.getScheme())
                && ALLOWED_HOST_SUFFIXES.stream().anyMatch(s -> host.equals(s) || host.endsWith("." + s));
        if (!ok) throw ApiException.badRequest("Unsupported push service.");
    }

    /** Queue a push to every browser of this user, after the current transaction commits. */
    public void sendToUser(Long userId, Map<String, Object> message) {
        if (push == null) return;
        Runnable job = () -> pool.submit(() -> deliver(userId, message));
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

    private void deliver(Long userId, Map<String, Object> message) {
        List<PushSubscription> list = tx.execute(s -> subs.findByUserId(userId));
        if (list == null || list.isEmpty()) return;
        String payload;
        try {
            payload = json.writeValueAsString(message);
        } catch (Exception e) {
            return;
        }
        for (PushSubscription sub : list) {
            try {
                checkEndpoint(sub.getEndpoint());
                var res = push.send(new Notification(sub.getEndpoint(), sub.getP256dh(), sub.getAuth(), payload), Encoding.AES128GCM);
                int code = res.getStatusLine().getStatusCode();
                if (code == 404 || code == 410) {
                    tx.executeWithoutResult(s -> subs.deleteByEndpoint(sub.getEndpoint()));
                } else if (code >= 400) {
                    log.debug("Push to user {} failed with HTTP {}", userId, code);
                } else {
                    log.debug("Push to user {} delivered to push service (HTTP {})", userId, code);
                }
            } catch (Exception e) {
                log.debug("Push to user {} failed: {}", userId, e.getMessage());
            }
        }
    }

    /** New P-256 key pair as base64url: uncompressed public point (65 bytes) and private scalar (32 bytes). */
    static String[] generateKeys() throws GeneralSecurityException {
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair kp = g.generateKeyPair();
        ECPublicKey pub = (ECPublicKey) kp.getPublic();
        byte[] point = new byte[65];
        point[0] = 0x04;
        System.arraycopy(fixed32(pub.getW().getAffineX()), 0, point, 1, 32);
        System.arraycopy(fixed32(pub.getW().getAffineY()), 0, point, 33, 32);
        byte[] d = fixed32(((ECPrivateKey) kp.getPrivate()).getS());
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        return new String[]{enc.encodeToString(point), enc.encodeToString(d)};
    }

    private static byte[] fixed32(BigInteger v) {
        byte[] raw = v.toByteArray();
        byte[] out = new byte[32];
        int copy = Math.min(raw.length, 32);
        System.arraycopy(raw, raw.length - copy, out, 32 - copy, copy);
        return out;
    }
}
