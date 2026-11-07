package com.tulas.canteen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tulas.canteen.domain.CanteenSettings;
import com.tulas.canteen.repo.DailyCounterRepository;
import com.tulas.canteen.repo.SettingsRepository;
import com.tulas.canteen.service.CounterService;
import com.tulas.canteen.service.DefaultMenu;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end API test: student orders, kitchen cooks, counter hands over with the OTP. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:tulas-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.seed-demo=true",
        "app.admin.password=Admin@123",
        "app.limits.signups-per-hour=1000"
})
class OrderFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired SettingsRepository settingsRepo;
    @Autowired CounterService counterService;
    @Autowired DailyCounterRepository counterRepo;
    @Autowired com.tulas.canteen.service.RecommendationService recommender;

    @BeforeEach
    void openAllDay() {
        // Tests can run at any hour, so stretch opening hours over the whole day.
        CanteenSettings s = settingsRepo.findById(CanteenSettings.SINGLETON_ID).orElseThrow();
        s.setOpenTime(LocalTime.MIN);
        s.setCloseTime(LocalTime.of(23, 59, 59));
        s.setUpiId("tulascanteen@okaxis");
        settingsRepo.save(s);
    }

    @Test
    void upiTopupApprovedOnceAndRejectedRequestsDontPay() throws Exception {
        String student = register("topup" + UUID.randomUUID() + "@college.edu");
        String admin = login("admin@tulas.local", "Admin@123");
        String ref = "UTR" + System.nanoTime();

        JsonNode req = call(post("/api/wallet/topups").content("{\"amount\":200,\"upiRef\":\"" + ref + "\"}"), student, 201);
        assertThat(req.get("status").asText()).isEqualTo("PENDING");
        // The same UPI reference can't be claimed twice.
        call(post("/api/wallet/topups").content("{\"amount\":200,\"upiRef\":\"" + ref + "\"}"), student, 409);
        // Nothing is credited until an admin approves.
        assertThat(call(get("/api/wallet"), student, 200).get("balance").decimalValue()).isEqualByComparingTo("0");

        long id = req.get("id").asLong();
        call(post("/api/admin/topups/" + id + "/approve"), admin, 200);
        call(post("/api/admin/topups/" + id + "/approve"), admin, 409); // second approval refused
        assertThat(call(get("/api/wallet"), student, 200).get("balance").decimalValue()).isEqualByComparingTo("200");

        JsonNode other = call(post("/api/wallet/topups").content("{\"amount\":500,\"upiRef\":\"X" + ref + "\"}"), student, 201);
        call(post("/api/admin/topups/" + other.get("id").asLong() + "/reject").content("{\"reason\":\"No such payment\"}"), admin, 200);
        assertThat(call(get("/api/wallet"), student, 200).get("balance").decimalValue()).isEqualByComparingTo("200");
        assertThat(call(get("/api/wallet/topups"), student, 200).get(0).get("reason").asText()).isEqualTo("No such payment");

        // Students can't approve their own.
        call(post("/api/admin/topups/" + id + "/approve"), student, 403);
        // It's in the activity log.
        JsonNode log = call(get("/api/admin/audit?q=TOPUP"), admin, 200).get("items");
        assertThat(log.toString()).contains("TOPUP_APPROVED").contains("TOPUP_REJECTED").contains(ref);
    }

    @Test
    void passwordChangeLogsOutOtherDevices() throws Exception {
        String email = "pw" + UUID.randomUUID() + "@college.edu";
        String phone = register(email);
        String laptop = login(email, "secret123");

        JsonNode res = call(post("/api/auth/password")
                .content("{\"currentPassword\":\"secret123\",\"newPassword\":\"newsecret456\"}"), phone, 200);
        String fresh = res.get("token").asText();

        call(get("/api/auth/me"), fresh, 200);   // the device that changed it stays in
        call(get("/api/auth/me"), laptop, 401);  // every other device is logged out
        call(get("/api/auth/me"), phone, 401);   // ...including the old token on this device
        login(email, "newsecret456");
    }

    @Test
    void adminResetPasswordGivesWorkingTemporaryPassword() throws Exception {
        String email = "reset" + UUID.randomUUID() + "@college.edu";
        String old = register(email);
        String admin = login("admin@tulas.local", "Admin@123");
        long id = call(get("/api/auth/me"), old, 200).get("id").asLong();

        String temp = call(post("/api/admin/users/" + id + "/reset-password"), admin, 200).get("tempPassword").asText();
        assertThat(temp).hasSize(10);
        call(get("/api/auth/me"), old, 401);
        login(email, temp);
    }

    @Test
    void repeatedWrongPasswordsLockTheAccountTemporarily() throws Exception {
        String email = "lock" + UUID.randomUUID() + "@college.edu";
        register(email);
        String wrong = "{\"email\":\"" + email + "\",\"password\":\"wrong-one\"}";
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(wrong))
                    .andExpect(status().isUnauthorized());
        }
        // Even the right password is refused while locked.
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"secret123\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void simultaneousOrdersGetUniqueNumbers() throws Exception {
        int n = 6;
        String[] tokens = new String[n];
        for (int i = 0; i < n; i++) tokens[i] = register("rush" + i + UUID.randomUUID() + "@college.edu");
        long chai = idOf(call(get("/api/menu"), tokens[0], 200), "Masala Chai");
        String body = "{\"items\":[{\"menuItemId\":" + chai + ",\"quantity\":1}],\"paymentMethod\":\"CASH\"}";

        var pool = Executors.newFixedThreadPool(n);
        var start = new CountDownLatch(1);
        List<Future<JsonNode>> placed = new ArrayList<>();
        for (String t : tokens) {
            placed.add(pool.submit(() -> {
                start.await();
                return call(post("/api/orders").content(body), t, 201);
            }));
        }
        start.countDown();
        Set<String> unique = new HashSet<>();
        List<JsonNode> results = new ArrayList<>();
        for (Future<JsonNode> f : placed) results.add(f.get(30, TimeUnit.SECONDS));
        pool.shutdown();
        results.forEach(o -> unique.add(o.get("code").asText()));
        assertThat(unique).hasSize(n);

        // Clean up so other tests see an empty queue.
        for (int i = 0; i < n; i++) call(post("/api/orders/" + results.get(i).get("id").asLong() + "/cancel"), tokens[i], 200);
    }

    @Test
    void firstOrderOfTheDayCounterSurvivesARace() throws Exception {
        LocalDate day = LocalDate.of(2099, 1, (int) (System.nanoTime() % 28) + 1);
        int n = 6;
        var pool = Executors.newFixedThreadPool(n);
        var start = new CountDownLatch(1);
        List<Future<?>> jobs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            jobs.add(pool.submit(() -> {
                start.await();
                try {
                    counterService.createIfMissing(day);
                } catch (DataIntegrityViolationException expectedForLosers) {
                    // fine: OrderService ignores this
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : jobs) f.get(30, TimeUnit.SECONDS); // any other exception fails the test
        pool.shutdown();
        assertThat(counterRepo.existsById(day)).isTrue();
    }

    /** Every admin list page, with and without its optional filters (empty filters broke on PostgreSQL). */
    @Test
    void adminListPagesWorkWithAndWithoutFilters() throws Exception {
        String admin = login("admin@tulas.local", "Admin@123");
        register("findme" + UUID.randomUUID() + "@college.edu");

        assertThat(call(get("/api/admin/users"), admin, 200).get("items").size()).isGreaterThan(0);
        assertThat(call(get("/api/admin/users?role=STUDENT"), admin, 200).get("items").size()).isGreaterThan(0);
        assertThat(call(get("/api/admin/users?q=findme"), admin, 200).get("items").size()).isEqualTo(1);
        assertThat(call(get("/api/admin/users?q=FINDME&role=STUDENT"), admin, 200).get("items").size()).isEqualTo(1);
        assertThat(call(get("/api/admin/users?q=findme&role=STAFF"), admin, 200).get("items").size()).isZero();
        assertThat(call(get("/api/admin/users?q=TU-CSE"), admin, 200).get("items").size()).isEqualTo(1); // roll number

        call(get("/api/admin/audit"), admin, 200);
        call(get("/api/admin/audit?q=recharge"), admin, 200);

        call(get("/api/admin/orders"), admin, 200);
        call(get("/api/admin/orders?status=COLLECTED"), admin, 200);
        mvc.perform(get("/api/admin/orders.csv").header("Authorization", "Bearer " + admin)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/orders.csv?status=CANCELLED").header("Authorization", "Bearer " + admin)).andExpect(status().isOk());

        call(get("/api/admin/topups"), admin, 200);
        call(get("/api/admin/topups?status=PENDING"), admin, 200);
        call(get("/api/admin/dashboard"), admin, 200);
        call(get("/api/admin/announcements"), admin, 200);
        call(get("/api/admin/settings"), admin, 200);
    }

    /** Every student and kitchen screen's data loads (so a database-specific query bug can't hide on a page). */
    @Test
    void studentAndKitchenPagesLoad() throws Exception {
        String student = register("pages" + UUID.randomUUID() + "@college.edu");
        String kitchen = login("kitchen@tulas.local", "kitchen123");
        long dish = idOf(call(get("/api/menu"), student, 200), "Masala Chai");

        call(get("/api/auth/me"), student, 200);
        call(get("/api/canteen"), student, 200);
        call(get("/api/announcements"), student, 200);
        call(get("/api/orders"), student, 200);
        call(get("/api/wallet"), student, 200);
        call(get("/api/wallet/spending"), student, 200);
        call(get("/api/wallet/topups"), student, 200);
        call(put("/api/favourites/" + dish), student, 200);
        assertThat(idOf(call(get("/api/menu"), student, 200), "Masala Chai")).isEqualTo(dish);
        call(delete("/api/favourites/" + dish), student, 200);
        call(put("/api/auth/me").content("{\"name\":\"Renamed Student\",\"phone\":\"9876500000\"}"), student, 200);

        call(get("/api/kitchen/queue"), kitchen, 200);
        call(get("/api/kitchen/today"), kitchen, 200);
        call(get("/api/kitchen/summary"), kitchen, 200);
        call(get("/api/kitchen/summary?day=2020-01-01"), kitchen, 200);
        call(put("/api/kitchen/menu/" + dish + "/stock").content("{\"stockToday\":null,\"available\":true}"), kitchen, 200);
    }

    @Test
    void rewardPointsAreEarnedOnCollectionUsedAtCheckoutAndReturnedOnCancel() throws Exception {
        String student = register("points" + UUID.randomUUID() + "@college.edu");
        String admin = login("admin@tulas.local", "Admin@123");
        String kitchen = login("kitchen@tulas.local", "kitchen123");
        long sid = call(get("/api/auth/me"), student, 200).get("id").asLong();
        call(post("/api/admin/users/" + sid + "/recharge").content("{\"amount\":500}"), admin, 200);
        long thali = idOf(call(get("/api/menu"), student, 200), "Veg Thali");
        long chai = idOf(call(get("/api/menu"), student, 200), "Masala Chai");

        // ₹180 order collected → 18 points (1 per ₹10, Bronze).
        JsonNode first = call(post("/api/orders").content(orderJson(thali, 2, "WALLET", 0)), student, 201);
        collect(first, kitchen);
        assertThat(call(get("/api/rewards"), student, 200).get("points").asInt()).isEqualTo(18);
        assertThat(call(get("/api/orders/" + first.get("id").asLong()), student, 200).get("pointsEarned").asInt()).isEqualTo(18);

        // Points rules: steps of 10, at most half the order.
        call(post("/api/orders").content(orderJson(thali, 1, "WALLET", 15)), student, 400);
        call(post("/api/orders").content(orderJson(chai, 1, "WALLET", 20)), student, 400); // ₹10 off a ₹15 order > half

        // Use 10 points = ₹5 off: pays ₹85 instead of ₹90.
        JsonNode second = call(post("/api/orders").content(orderJson(thali, 1, "WALLET", 10)), student, 201);
        assertThat(second.get("total").decimalValue()).isEqualByComparingTo("85");
        assertThat(second.get("discount").decimalValue()).isEqualByComparingTo("5");
        assertThat(call(get("/api/rewards"), student, 200).get("points").asInt()).isEqualTo(8);
        assertThat(call(get("/api/wallet"), student, 200).get("balance").decimalValue()).isEqualByComparingTo("235");

        // Cancel: money and points both come back.
        call(post("/api/orders/" + second.get("id").asLong() + "/cancel"), student, 200);
        assertThat(call(get("/api/rewards"), student, 200).get("points").asInt()).isEqualTo(18);
        assertThat(call(get("/api/wallet"), student, 200).get("balance").decimalValue()).isEqualByComparingTo("320");
        // Can't spend more points than you have.
        call(post("/api/orders").content(orderJson(thali, 2, "WALLET", 20)), student, 400);
    }

    @Test
    void referralRewardsBothFriendsAfterFirstCollectedOrder() throws Exception {
        String inviter = register("inviter" + UUID.randomUUID() + "@college.edu");
        String kitchen = login("kitchen@tulas.local", "kitchen123");
        String code = call(get("/api/rewards"), inviter, 200).get("referralCode").asText();
        assertThat(code).hasSize(7);

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"email\":\"bad" + UUID.randomUUID() + "@c.edu\",\"password\":\"secret123\",\"referralCode\":\"NOPE999\"}"))
                .andExpect(status().isBadRequest());

        String body = "{\"name\":\"Friend\",\"email\":\"friend" + UUID.randomUUID() + "@college.edu\",\"password\":\"secret123\",\"referralCode\":\"" + code.toLowerCase() + "\"}";
        String friend = json.readTree(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("token").asText();

        long chai = idOf(call(get("/api/menu"), friend, 200), "Masala Chai");
        collect(call(post("/api/orders").content(orderJson(chai, 1, "CASH", 0)), friend, 201), kitchen);
        assertThat(call(get("/api/rewards"), friend, 200).get("points").asInt()).isEqualTo(50 + 1);
        assertThat(call(get("/api/rewards"), inviter, 200).get("points").asInt()).isEqualTo(50);
        // Only once.
        collect(call(post("/api/orders").content(orderJson(chai, 1, "CASH", 0)), friend, 201), kitchen);
        assertThat(call(get("/api/rewards"), inviter, 200).get("points").asInt()).isEqualTo(50);
    }

    @Test
    void birthdayCanBeSetOnceAndMustBeReal() throws Exception {
        String student = register("bday" + UUID.randomUUID() + "@college.edu");
        call(put("/api/rewards/birthday").content("{\"birthday\":\"2024-01-01\"}"), student, 400); // too young
        call(put("/api/rewards/birthday").content("{\"birthday\":\"2004-06-15\"}"), student, 200);
        call(put("/api/rewards/birthday").content("{\"birthday\":\"2004-06-16\"}"), student, 400); // only once
    }

    @Test
    void recommendationsLearnWhatIsOrderedTogether() throws Exception {
        String kitchen = login("kitchen@tulas.local", "kitchen123");
        String probe = register("probe" + UUID.randomUUID() + "@college.edu");
        JsonNode menu = call(get("/api/menu"), probe, 200);
        long dosa = idOf(menu, "Masala Dosa"), coffee = idOf(menu, "Filter Coffee");

        // Four students order dosa + filter coffee together.
        for (int i = 0; i < 4; i++) {
            String s = register("pair" + i + UUID.randomUUID() + "@college.edu");
            String body = "{\"items\":[{\"menuItemId\":" + dosa + ",\"quantity\":1},{\"menuItemId\":" + coffee
                    + ",\"quantity\":1}],\"paymentMethod\":\"CASH\"}";
            collect(call(post("/api/orders").content(body), s, 201), kitchen);
        }
        // The probe student has only ever had dosa.
        collect(call(post("/api/orders").content(orderJson(dosa, 1, "CASH", 0)), probe, 201), kitchen);
        recommender.refresh();

        JsonNode cart = call(post("/api/recommendations/cart").content("{\"itemIds\":[" + dosa + "]}"), probe, 200);
        assertThat(cart.get(0).get("menuItemId").asLong()).isEqualTo(coffee);
        assertThat(cart.get(0).get("reason").asText()).contains("Masala Dosa");

        JsonNode feed = call(get("/api/recommendations"), probe, 200);
        assertThat(feed.get("orderAgain").get(0).get("menuItemId").asLong()).isEqualTo(dosa);
        assertThat(feed.get("forYou").get(0).get("menuItemId").asLong()).isEqualTo(coffee);
        assertThat(feed.get("forYou").get(0).get("reason").asText()).isEqualTo("Goes well with Masala Dosa");

        // Alternatives to dosa: other veg breakfast dishes, never the dish itself or a drink.
        JsonNode similar = call(get("/api/recommendations/similar/" + dosa), probe, 200);
        assertThat(similar.size()).isEqualTo(3);
        for (JsonNode r : similar) {
            JsonNode d = dish(menu, r.get("menuItemId").asLong());
            assertThat(d.get("category").asText()).isEqualTo("BREAKFAST");
            assertThat(d.get("veg").asBoolean()).isTrue();
            assertThat(d.get("id").asLong()).isNotEqualTo(dosa);
        }
    }

    private static JsonNode dish(JsonNode menu, long id) {
        for (JsonNode m : menu) if (m.get("id").asLong() == id) return m;
        throw new AssertionError("No dish " + id);
    }

    @Test
    void standardMenuImportSkipsExistingDishes() throws Exception {
        String admin = login("admin@tulas.local", "Admin@123");
        // Demo mode already loaded the standard menu, so everything is skipped.
        JsonNode r = call(post("/api/admin/menu/import-defaults"), admin, 200);
        assertThat(r.get("added").asInt()).isZero();
        assertThat(r.get("skipped").asInt()).isEqualTo(DefaultMenu.DISHES.size());
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

    private static String orderJson(long dishId, int qty, String pay, int redeemPoints) {
        return "{\"items\":[{\"menuItemId\":" + dishId + ",\"quantity\":" + qty + "}],\"paymentMethod\":\"" + pay
                + "\",\"redeemPoints\":" + redeemPoints + "}";
    }

    /** Kitchen marks the order ready and hands it over with the student's code. */
    private void collect(JsonNode order, String kitchen) throws Exception {
        call(post("/api/kitchen/orders/" + order.get("id").asLong() + "/status").content("{\"status\":\"READY\"}"), kitchen, 200);
        call(post("/api/kitchen/verify").content("{\"code\":\"" + order.get("code").asText() + "\",\"otp\":\""
                + order.get("otp").asText() + "\"}"), kitchen, 200);
    }

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
