package com.tulas.canteen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tulas.canteen.domain.CanteenSettings;
import com.tulas.canteen.repo.SettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end API test: student orders, kitchen cooks, counter hands over with the OTP. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:tulas-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.seed-demo=true"
})
class OrderFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired SettingsRepository settingsRepo;

    @BeforeEach
    void openAllDay() {
        // Tests can run at any hour, so stretch opening hours over the whole day.
        CanteenSettings s = settingsRepo.findById(CanteenSettings.SINGLETON_ID).orElseThrow();
        s.setOpenTime(LocalTime.MIN);
        s.setCloseTime(LocalTime.of(23, 59, 59));
        settingsRepo.save(s);
    }

    @Test
    void fullOrderLifecycle() throws Exception {
        String student = register("asha" + UUID.randomUUID() + "@college.edu");
        String kitchen = login("kitchen@tulas.local", "kitchen123");
        String admin = login("admin@tulas.local", "Admin@123");

        // New students start with ₹0: wallet order is refused.
        JsonNode menu = call(get("/api/menu"), student, 200);
        long dosaId = idOf(menu, "Masala Dosa");
        int dosaStock = stockOf(menu, "Masala Dosa");
        String order = "{\"items\":[{\"menuItemId\":" + dosaId + ",\"quantity\":2}],\"paymentMethod\":\"WALLET\"}";
        JsonNode err = call(post("/api/orders").content(order), student, 400);
        assertThat(err.get("message").asText()).contains("Not enough wallet balance");

        // Admin recharges ₹300.
        long studentId = call(get("/api/auth/me"), student, 200).get("id").asLong();
        call(post("/api/admin/users/" + studentId + "/recharge").content("{\"amount\":300}"), admin, 200);

        // Place the order twice with the same Idempotency-Key: only one order, charged once.
        String key = UUID.randomUUID().toString();
        JsonNode placed = call(post("/api/orders").header("Idempotency-Key", key).content(order), student, 201);
        JsonNode again = call(post("/api/orders").header("Idempotency-Key", key).content(order), student, 201);
        assertThat(again.get("id").asLong()).isEqualTo(placed.get("id").asLong());
        assertThat(placed.get("total").decimalValue()).isEqualByComparingTo("120");
        assertThat(placed.get("code").asText()).matches("T-\\d{3}");
        assertThat(placed.get("otp").asText()).matches("\\d{4}");
        assertThat(placed.get("ahead").asLong()).isZero();
        assertThat(call(get("/api/wallet"), student, 200).get("balance").decimalValue()).isEqualByComparingTo("180");
        assertThat(stockOf(call(get("/api/menu"), student, 200), "Masala Dosa")).isEqualTo(dosaStock - 2);

        long id = placed.get("id").asLong();
        String code = placed.get("code").asText();
        String otp = placed.get("otp").asText();

        // Kitchen sees it without the OTP.
        JsonNode queue = call(get("/api/kitchen/queue"), kitchen, 200);
        JsonNode inQueue = null;
        for (JsonNode o : queue) if (o.get("id").asLong() == id) inQueue = o;
        assertThat(inQueue).isNotNull();
        assertThat(inQueue.get("otp").isNull()).isTrue();

        // Students can't use kitchen endpoints; kitchen can't use admin endpoints.
        call(get("/api/kitchen/queue"), student, 403);
        call(get("/api/admin/dashboard"), kitchen, 403);

        call(post("/api/kitchen/orders/" + id + "/status").content("{\"status\":\"PREPARING\"}"), kitchen, 200);
        // Student can no longer cancel once cooking started.
        call(post("/api/orders/" + id + "/cancel"), student, 400);
        call(post("/api/kitchen/orders/" + id + "/status").content("{\"status\":\"READY\"}"), kitchen, 200);

        // Wrong OTP is counted; right OTP hands over.
        String wrong = otp.equals("0000") ? "1111" : "0000";
        JsonNode bad = call(post("/api/kitchen/verify").content("{\"code\":\"" + code + "\",\"otp\":\"" + wrong + "\"}"), kitchen, 422);
        assertThat(bad.get("attemptsLeft").asInt()).isEqualTo(4);
        JsonNode ok = call(post("/api/kitchen/verify").content("{\"code\":\"" + code.toLowerCase() + "\",\"otp\":\"" + otp + "\"}"), kitchen, 200);
        assertThat(ok.get("order").get("status").asText()).isEqualTo("COLLECTED");
        call(post("/api/kitchen/verify").content("{\"code\":\"" + code + "\",\"otp\":\"" + otp + "\"}"), kitchen, 409);

        // Rate once.
        call(post("/api/orders/" + id + "/rate").content("{\"stars\":5}"), student, 200);
        call(post("/api/orders/" + id + "/rate").content("{\"stars\":4}"), student, 400);

        // A second order cancelled by the student is refunded and its plates return.
        JsonNode second = call(post("/api/orders").content(order), student, 201);
        assertThat(call(get("/api/wallet"), student, 200).get("balance").decimalValue()).isEqualByComparingTo("60");
        call(post("/api/orders/" + second.get("id").asLong() + "/cancel"), student, 200);
        assertThat(call(get("/api/wallet"), student, 200).get("balance").decimalValue()).isEqualByComparingTo("180");
        assertThat(stockOf(call(get("/api/menu"), student, 200), "Masala Dosa")).isEqualTo(dosaStock - 2);

        // Reports.
        JsonNode summary = call(get("/api/kitchen/summary"), kitchen, 200);
        assertThat(summary.get("collected").asLong()).isGreaterThanOrEqualTo(1);
        JsonNode dash = call(get("/api/admin/dashboard"), admin, 200);
        assertThat(dash.get("last7Days").size()).isEqualTo(7);
        mvc.perform(get("/api/admin/orders.csv").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
    }

    @Test
    void closedCanteenRejectsOrders() throws Exception {
        String student = register("ravi" + UUID.randomUUID() + "@college.edu");
        String kitchen = login("kitchen@tulas.local", "kitchen123");
        call(put("/api/kitchen/status").content("{\"status\":\"CLOSED\"}"), kitchen, 200);
        try {
            long id = idOf(call(get("/api/menu"), student, 200), "Masala Chai");
            String order = "{\"items\":[{\"menuItemId\":" + id + ",\"quantity\":1}],\"paymentMethod\":\"CASH\"}";
            JsonNode err = call(post("/api/orders").content(order), student, 400);
            assertThat(err.get("message").asText()).contains("isn't taking orders");
        } finally {
            call(put("/api/kitchen/status").content("{\"status\":\"OPEN\"}"), kitchen, 200);
        }
    }

    @Test
    void unauthenticatedAndBadLogin() throws Exception {
        mvc.perform(get("/api/menu")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@tulas.local\",\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ---- helpers

    private String register(String email) throws Exception {
        String body = "{\"name\":\"Test Student\",\"email\":\"" + email + "\",\"password\":\"secret123\",\"rollNo\":\"TU-1\"}";
        String res = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("token").asText();
    }

    private String login(String email, String password) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
        String res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("token").asText();
    }

    private JsonNode call(MockHttpServletRequestBuilder req, String token, int expected) throws Exception {
        String res = mvc.perform(req.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is(expected))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return res.isEmpty() ? json.nullNode() : json.readTree(res);
    }

    private static long idOf(JsonNode menu, String name) {
        for (JsonNode m : menu) if (m.get("name").asText().equals(name)) return m.get("id").asLong();
        throw new AssertionError("No dish " + name);
    }

    private static int stockOf(JsonNode menu, String name) {
        for (JsonNode m : menu) if (m.get("name").asText().equals(name)) return m.get("stockToday").asInt();
        throw new AssertionError("No dish " + name);
    }
}
