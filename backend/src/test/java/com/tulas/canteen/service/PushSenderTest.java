package com.tulas.canteen.service;

import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.util.EntityUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.security.Security;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Our generated VAPID keys work with the Web Push library, and requests are encrypted and signed. */
class PushSenderTest {

    @Test
    void generatedKeysProduceAnEncryptedSignedPushRequest() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        String[] server = PushSender.generateKeys();
        PushService push = new PushService(server[0], server[1], "mailto:admin@tulas.local");

        // A browser's subscription: its own P-256 public key and a 16-byte auth secret.
        String browserKey = PushSender.generateKeys()[0];
        byte[] auth = new byte[16];
        new SecureRandom().nextBytes(auth);
        String payload = "{\"title\":\"Your food is ready!\"}";
        Notification n = new Notification("https://fcm.googleapis.com/fcm/send/abc", browserKey,
                Base64.getUrlEncoder().withoutPadding().encodeToString(auth), payload);

        HttpPost post = push.preparePost(n, Encoding.AES128GCM);
        assertThat(post.getFirstHeader("Content-Encoding").getValue()).isEqualTo("aes128gcm");
        assertThat(post.getFirstHeader("Authorization").getValue()).startsWith("vapid t=").contains("k=" + server[0]);
        byte[] body = EntityUtils.toByteArray(post.getEntity());
        assertThat(body.length).isGreaterThan(payload.length()); // encrypted record + header
        assertThat(new String(body)).doesNotContain("Your food is ready"); // not readable in transit
    }

    @Test
    void onlyOfficialPushServicesAreAllowed() {
        PushSender.checkEndpoint("https://fcm.googleapis.com/fcm/send/x");
        PushSender.checkEndpoint("https://updates.push.services.mozilla.com/wpush/v2/x");
        PushSender.checkEndpoint("https://web.push.apple.com/x");
        PushSender.checkEndpoint("https://wns2-par02p.notify.windows.com/w/?token=x");
        assertThatThrownBy(() -> PushSender.checkEndpoint("http://fcm.googleapis.com/x")).hasMessageContaining("Unsupported");
        assertThatThrownBy(() -> PushSender.checkEndpoint("https://localhost:8080/x")).hasMessageContaining("Unsupported");
        assertThatThrownBy(() -> PushSender.checkEndpoint("https://notfcm.googleapis.com.attacker.io/x")).hasMessageContaining("Unsupported");
    }
}
