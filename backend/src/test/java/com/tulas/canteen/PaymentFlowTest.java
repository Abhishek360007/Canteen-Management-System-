package com.tulas.canteen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.tulas.canteen.repo.CanteenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Online payment against a fake Razorpay API (a real local HTTP server), with real signature checks.
 * Covers: amounts fixed on the server, bad signatures refused, credit exactly once (browser + webhook),
 * webhook-only credit when the browser was closed, limits, the admin switch, and paying at checkout.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:tulas-pay;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.seed-demo=true",
        "app.admin.password=Admin@123",
        "app.limits.signups-per-hour=1000",
        "app.razorpay.key-id=rzp_test_dummykey",
        "app.razorpay.key-secret=test_key_secret",
        "app.razorpay.webhook-secret=test_webhook_secret"
})
@AutoConfigureMockMvc
class PaymentFlowTest {

    static final String KEY_SECRET = "test_key_secret";
    static final String WEBHOOK_SECRET = "test_webhook_secret";

    // ---- fake Razorpay: POST /v1/orders
    static final HttpServer FAKE;
    static final AtomicInteger ORDERS = new AtomicInteger();
    static final AtomicReference<String> LAST_AUTH = new AtomicReference<>();
    static final AtomicReference<String> LAST_BODY = new AtomicReference<>();

    static {
        try {
            FAKE = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        FAKE.createContext("/v1/orders", ex -> {
            LAST_AUTH.set(ex.getRequestHeaders().getFirst("Authorization"));
            LAST_BODY.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] res = ("{\"id\":\"order_fake" + ORDERS.incrementAndGet() + "\",\"status\":\"created\"}").getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, res.length);
            ex.getResponseBody().write(res);
            ex.close();
        });
        FAKE.start();
    }

    @DynamicPropertySource
    static void razorpayBase(DynamicPropertyRegistry r) {
        r.add("app.razorpay.api-base", () -> "http://127.0.0.1:" + FAKE.getAddress().getPort() + "/v1");
    }

    // The fake server is shared with PostgresPaymentFlowTest (same static field), so it is not stopped per class;
    // it ends with the test JVM.

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CanteenRepository canteens;

    @BeforeEach
    void openAllDay() {
        for (var c : canteens.findAll()) {
            c.setOpenTime(LocalTime.MIN);
            c.setCloseTime(LocalTime.of(23, 59, 59));
            canteens.save(c);
        }
    }

    @Test
    void walletTopUpIsCreditedExactlyOnceAfterAValidSignature() throws Exception {
        String student = register();
        JsonNode cfg = call(get("/api/payments/config"), student, 200);
        assertThat(cfg.get("online").asBoolean()).isTrue();
        assertThat(cfg.get("mode").asText()).isEqualTo("TEST");
        assertThat(cfg.get("keyId").asText()).isEqualTo("rzp_test_dummykey");
        assertThat(cfg.toString()).doesNotContain(KEY_SECRET);

        JsonNode order = call(post("/api/payments/razorpay/order").content("{\"amount\":200,\"purpose\":\"wallet\"}"), student, 200);
        assertThat(order.get("amount").asLong()).isEqualTo(20000); // paise, fixed by the server
        // The server called Razorpay with Basic auth (key id : secret) and the right amount.
        assertThat(new String(Base64.getDecoder().decode(LAST_AUTH.get().substring(6)))).isEqualTo("rzp_test_dummykey:" + KEY_SECRET);
        assertThat(LAST_BODY.get()).contains("\"amount\":20000").contains("\"currency\":\"INR\"");

        String orderId = order.get("orderId").asText();
        String paymentId = "pay_" + UUID.randomUUID().toString().substring(0, 14);

        // A forged signature is refused and nothing is credited.
        call(post("/api/payments/razorpay/verify").content(confirmJson(orderId, paymentId, "deadbeef")), student, 400);
        assertThat(balance(student)).isEqualByComparingTo("0");

        JsonNode ok = call(post("/api/payments/razorpay/verify").content(confirmJson(orderId, paymentId, sign(orderId + "|" + paymentId))), student, 200);
        assertThat(ok.get("credited").asBoolean()).isTrue();
        assertThat(balance(student)).isEqualByComparingTo("200");

        // The same payment confirmed again, and then Razorpay's webhook for it: still ₹200, not ₹400 or ₹600.
        assertThat(call(post("/api/payments/razorpay/verify").content(confirmJson(orderId, paymentId, sign(orderId + "|" + paymentId))), student, 200)
                .get("credited").asBoolean()).isFalse();
        webhook("payment.captured", orderId, paymentId, 20000, true, 200);
        assertThat(balance(student)).isEqualByComparingTo("200");

        // Another student can't confirm this order.
        String other = register();
        call(post("/api/payments/razorpay/verify").content(confirmJson(orderId, paymentId, sign(orderId + "|" + paymentId))), other, 404);
    }

    @Test
    void webhookCreditsWhenTheBrowserWasClosed() throws Exception {
        String student = register();
        String orderId = call(post("/api/payments/razorpay/order").content("{\"amount\":150}"), student, 200).get("orderId").asText();
        String paymentId = "pay_" + UUID.randomUUID().toString().substring(0, 14);

        webhook("payment.captured", orderId, paymentId, 15000, false, 400); // forged webhook
        webhook("payment.captured", orderId, paymentId, 99999, true, 200);  // amount doesn't match: ignored
        assertThat(balance(student)).isEqualByComparingTo("0");
        webhook("payment.captured", orderId, paymentId, 15000, true, 200);
        assertThat(balance(student)).isEqualByComparingTo("150");
        webhook("order.paid", orderId, paymentId, 15000, true, 200);       // a second event for the same payment
        assertThat(balance(student)).isEqualByComparingTo("150");
    }

    @Test
    void limitsAndAdminSwitch() throws Exception {
        String student = register();
        String admin = login("admin@tulas.local", "Admin@123");
        call(post("/api/payments/razorpay/order").content("{\"amount\":10,\"purpose\":\"wallet\"}"), student, 400);   // below ₹50
        call(post("/api/payments/razorpay/order").content("{\"amount\":9000,\"purpose\":\"wallet\"}"), student, 400); // above ₹5000
        call(post("/api/payments/razorpay/order").content("{\"amount\":5,\"purpose\":\"checkout\"}"), student, 200);  // shortfall may be small
        call(get("/api/payments/config"), login("kitchen@tulas.local", "kitchen123"), 403);

        JsonNode s = call(get("/api/admin/payments/settings"), admin, 200);
        assertThat(s.get("keysConfigured").asBoolean()).isTrue();
        assertThat(s.get("webhookConfigured").asBoolean()).isTrue();
        call(put("/api/admin/payments/settings").content("{\"onlineEnabled\":false,\"minTopup\":50,\"maxTopup\":5000}"), admin, 200);
        assertThat(call(get("/api/payments/config"), student, 200).get("online").asBoolean()).isFalse();
        call(post("/api/payments/razorpay/order").content("{\"amount\":100}"), student, 400);
        call(put("/api/admin/payments/settings").content("{\"onlineEnabled\":true,\"minTopup\":50,\"maxTopup\":5000}"), admin, 200);
        assertThat(call(get("/api/admin/payments"), admin, 200).size()).isGreaterThan(0);
    }

    @Test
    void payingTheShortfallAtCheckoutThenPlacingTheOrder() throws Exception {
        String student = register();
        long thali = 0;
        for (JsonNode m : call(get("/api/menu"), student, 200)) if (m.get("name").asText().equals("Veg Thali")) thali = m.get("id").asLong();
        String orderBody = "{\"items\":[{\"menuItemId\":" + thali + ",\"quantity\":1}],\"paymentMethod\":\"WALLET\"}";
        call(post("/api/orders").content(orderBody), student, 400); // ₹0 in wallet

        String orderId = call(post("/api/payments/razorpay/order").content("{\"amount\":90,\"purpose\":\"checkout\"}"), student, 200).get("orderId").asText();
        String paymentId = "pay_" + UUID.randomUUID().toString().substring(0, 14);
        call(post("/api/payments/razorpay/verify").content(confirmJson(orderId, paymentId, sign(orderId + "|" + paymentId))), student, 200);
        JsonNode placed = call(post("/api/orders").content(orderBody), student, 201);
        assertThat(placed.get("paymentMethod").asText()).isEqualTo("WALLET");
        assertThat(balance(student)).isEqualByComparingTo("0");
        // Cancelling refunds to the wallet (where online money lives), as with any wallet order.
        call(post("/api/orders/" + placed.get("id").asLong() + "/cancel"), student, 200);
        assertThat(balance(student)).isEqualByComparingTo("90");
    }

    // ---- helpers

    private void webhook(String event, String orderId, String paymentId, long amount, boolean validSignature, int expect) throws Exception {
        String body = "{\"event\":\"" + event + "\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"" + paymentId
                + "\",\"order_id\":\"" + orderId + "\",\"amount\":" + amount + ",\"status\":\"captured\"}}}}";
        String sig = validSignature ? hmac(WEBHOOK_SECRET, body) : hmac("wrong_secret", body);
        mvc.perform(post("/api/payments/razorpay/webhook").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", sig).content(body))
                .andExpect(status().is(expect));
    }

    private static String confirmJson(String orderId, String paymentId, String signature) {
        return "{\"orderId\":\"" + orderId + "\",\"paymentId\":\"" + paymentId + "\",\"signature\":\"" + signature + "\"}";
    }

    private static String sign(String data) {
        return hmac(KEY_SECRET, data);
    }

    private static String hmac(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private java.math.BigDecimal balance(String token) throws Exception {
        return call(get("/api/wallet"), token, 200).get("balance").decimalValue();
    }

    private String register() throws Exception {
        String body = "{\"name\":\"Pay Tester\",\"email\":\"pay" + UUID.randomUUID() + "@college.edu\",\"password\":\"secret123\"}";
        String res = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("token").asText();
    }

    private String login(String email, String password) throws Exception {
        String res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("token").asText();
    }

    private JsonNode call(MockHttpServletRequestBuilder req, String token, int expected) throws Exception {
        String res = mvc.perform(req.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return res.isEmpty() ? json.nullNode() : json.readTree(res);
    }
}
