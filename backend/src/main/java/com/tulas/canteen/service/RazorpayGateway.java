package com.tulas.canteen.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.tulas.canteen.config.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

/**
 * Talks to Razorpay (https://razorpay.com/docs/api/): creates payment orders and checks signatures.
 * Keys come only from environment variables (RAZORPAY_KEY_ID / RAZORPAY_KEY_SECRET / RAZORPAY_WEBHOOK_SECRET);
 * they are never stored in the database or shown in the app. Without keys, online payment is simply off.
 */
@Component
public class RazorpayGateway {

    private final String keyId;
    private final String keySecret;
    private final String webhookSecret;
    private final RestClient http;

    public RazorpayGateway(@Value("${app.razorpay.key-id:}") String keyId,
                           @Value("${app.razorpay.key-secret:}") String keySecret,
                           @Value("${app.razorpay.webhook-secret:}") String webhookSecret,
                           @Value("${app.razorpay.api-base:https://api.razorpay.com/v1}") String apiBase) {
        this.keyId = keyId.trim();
        this.keySecret = keySecret.trim();
        this.webhookSecret = webhookSecret.trim();
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(5_000);
        timeouts.setReadTimeout(15_000);
        this.http = RestClient.builder()
                .baseUrl(apiBase)
                .requestFactory(timeouts)
                .defaultHeader("Authorization", "Basic " + Base64.getEncoder()
                        .encodeToString((this.keyId + ":" + this.keySecret).getBytes(StandardCharsets.UTF_8)))
                .build();
    }

    public boolean configured() {
        return !keyId.isEmpty() && !keySecret.isEmpty();
    }

    public boolean webhookConfigured() {
        return !webhookSecret.isEmpty();
    }

    /** "TEST" for rzp_test_ keys (no real money), "LIVE" for rzp_live_ keys, "OFF" without keys. */
    public String mode() {
        if (!configured()) return "OFF";
        return keyId.startsWith("rzp_live_") ? "LIVE" : "TEST";
    }

    /** The public key id the browser checkout needs (safe to share; the secret never leaves the server). */
    public String keyId() {
        return keyId;
    }

    /** Creates a Razorpay order for this many paise and returns its id (order_...). */
    public String createOrder(long amountPaise, String receipt, Map<String, String> notes) {
        if (!configured()) throw ApiException.badRequest("Online payment isn't set up yet.");
        try {
            JsonNode res = http.post().uri("/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("amount", amountPaise, "currency", "INR", "receipt", receipt, "notes", notes))
                    .retrieve()
                    .body(JsonNode.class);
            if (res == null || !res.hasNonNull("id")) throw new RestClientException("no order id");
            return res.get("id").asText();
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Couldn't reach the payment service. Please try again.");
        }
    }

    /** Signature the browser gets after a successful payment: HMAC-SHA256(order_id + "|" + payment_id, key secret). */
    public boolean checkPaymentSignature(String orderId, String paymentId, String signature) {
        return configured() && matches(hmac(keySecret, orderId + "|" + paymentId), signature);
    }

    /** Webhook signature: HMAC-SHA256(raw request body, webhook secret), in the X-Razorpay-Signature header. */
    public boolean checkWebhookSignature(String rawBody, String signature) {
        return webhookConfigured() && matches(hmac(webhookSecret, rawBody), signature);
    }

    static String hmac(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Constant-time comparison, so the check can't be guessed byte by byte from response timing. */
    private static boolean matches(String expected, String given) {
        if (given == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), given.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }
}
