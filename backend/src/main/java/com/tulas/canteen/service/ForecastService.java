package com.tulas.canteen.service;

import com.tulas.canteen.domain.*;
import com.tulas.canteen.repo.DishIngredientRepository;
import com.tulas.canteen.repo.MenuItemRepository;
import com.tulas.canteen.repo.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Demand forecasting from the canteen's own sales.
 *
 * <p><b>Method (per dish, per day).</b> Canteen demand has a strong weekly rhythm (Mondays differ from
 * Saturdays) and drifts with the term. So the forecast blends:
 * <ul>
 *   <li>the same weekday over the last 4 weeks, weighted 40/30/20/10% (recent weeks count more), and</li>
 *   <li>an exponentially weighted average of the last 7 open days (α = 0.3) for the recent trend.</li>
 * </ul>
 * 60/40 when there are at least two past same-weekdays, otherwise the recent average alone.
 * Days the canteen had no orders at all (holidays, closed) are left out so they don't drag forecasts to zero.
 * A 10% safety margin gives the recommended plates; the low–high range comes from past forecast errors.
 *
 * <p><b>Checked against reality.</b> {@link #accuracy} replays the last 14 open days: for each day it forecasts
 * using only the data before that day, then compares with what actually sold.
 */
@Service
public class ForecastService {

    private static final Set<OrderStatus> SOLD = EnumSet.of(OrderStatus.PLACED, OrderStatus.PREPARING,
            OrderStatus.READY, OrderStatus.COLLECTED, OrderStatus.NO_SHOW);
    private static final double[] WEEKDAY_WEIGHTS = {0.4, 0.3, 0.2, 0.1};
    private static final double ALPHA = 0.3;
    private static final double SAFETY = 1.10;
    static final int HISTORY_DAYS = 56;

    private final OrderRepository orders;
    private final MenuItemRepository menu;
    private final DishIngredientRepository recipes;
    private final Clock clock;

    public ForecastService(OrderRepository orders, MenuItemRepository menu, DishIngredientRepository recipes, Clock clock) {
        this.orders = orders;
        this.menu = menu;
        this.recipes = recipes;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- data

    /** Plates sold per dish per day, and which days the canteen was open (had any sale). */
    private record History(LocalDate from, Map<LocalDate, Map<Long, Integer>> byDay, SortedSet<LocalDate> openDays, List<Order> raw) {
        int qty(LocalDate d, Long dish) {
            return byDay.getOrDefault(d, Map.of()).getOrDefault(dish, 0);
        }

        int total(LocalDate d) {
            return byDay.getOrDefault(d, Map.of()).values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    private History history(Long canteenId, LocalDate from, LocalDate to) {
        List<Order> list = orders.findByCanteenIdAndOrderDateBetween(canteenId, from, to).stream()
                .filter(o -> SOLD.contains(o.getStatus())).toList();
        Map<LocalDate, Map<Long, Integer>> byDay = new HashMap<>();
        for (Order o : list) {
            Map<Long, Integer> day = byDay.computeIfAbsent(o.getOrderDate(), k -> new HashMap<>());
            o.getItems().forEach(i -> day.merge(i.getMenuItemId(), i.getQuantity(), Integer::sum));
        }
        return new History(from, byDay, new TreeSet<>(byDay.keySet()), list);
    }

    // ---------------------------------------------------------------- the model

    public record Prediction(double value, String basis) {}

    /** Forecast plates of one dish for {@code target}, using only open days strictly before it. */
    static Prediction predict(History h, Long dish, LocalDate target) {
        List<LocalDate> before = h.openDays().headSet(target).stream().sorted(Comparator.reverseOrder()).toList();
        if (before.isEmpty()) return new Prediction(0, "No sales history yet");

        // Same weekday, last 4 occurrences that were open days.
        DayOfWeek dow = target.getDayOfWeek();

        // A weekday that came round at least twice since sales began and was closed every time: expect closed.
        LocalDate start = h.openDays().first().isAfter(h.from()) ? h.openDays().first() : h.from();
        int seen = 0;
        for (LocalDate d = target.minusWeeks(1); !d.isBefore(start); d = d.minusWeeks(1)) seen++;
        boolean everOpen = before.stream().anyMatch(d -> d.getDayOfWeek() == dow);
        if (seen >= 2 && !everOpen) return new Prediction(0, "Usually closed on " + pretty(dow) + "s");
        List<LocalDate> sameDay = before.stream().filter(d -> d.getDayOfWeek() == dow).limit(4).toList();
        double sw = 0, wsum = 0;
        for (int i = 0; i < sameDay.size(); i++) {
            sw += WEEKDAY_WEIGHTS[i] * h.qty(sameDay.get(i), dish);
            wsum += WEEKDAY_WEIGHTS[i];
        }
        double weekdayAvg = wsum == 0 ? 0 : sw / wsum;

        // EWMA over the last 7 open days, oldest first.
        List<LocalDate> recent = new ArrayList<>(before.stream().limit(7).toList());
        Collections.reverse(recent);
        double ewma = h.qty(recent.get(0), dish);
        for (int i = 1; i < recent.size(); i++) ewma = ALPHA * h.qty(recent.get(i), dish) + (1 - ALPHA) * ewma;

        if (sameDay.size() >= 2) {
            return new Prediction(0.6 * weekdayAvg + 0.4 * ewma,
                    "Last " + sameDay.size() + " " + pretty(dow) + "s + recent trend");
        }
        return new Prediction(ewma, "Recent " + recent.size() + "-day trend");
    }

    // ---------------------------------------------------------------- forecast for a day

    public record DishForecast(Long id, String name, Category category, String emoji, double predicted,
                               int recommendedPlates, int low, int high, String basis, Integer currentDailyStock) {}

    public record IngredientNeed(String name, String unit, BigDecimal quantity) {}

    public record Forecast(LocalDate date, int openDaysOfHistory, int totalPlates, List<DishForecast> dishes,
                           List<IngredientNeed> ingredients, String method) {}

    @Transactional(readOnly = true)
    public Forecast forecast(Long canteenId, LocalDate target) {
        History h = history(canteenId, target.minusDays(HISTORY_DAYS), target.minusDays(1));
        double errorSpread = residualStd(h, target);
        List<DishForecast> list = new ArrayList<>();
        for (MenuItem d : menu.findByCanteenIdAndArchivedFalseOrderByCategoryAscNameAsc(canteenId)) {
            Prediction p = predict(h, d.getId(), target);
            int rec = (int) Math.ceil(p.value() * SAFETY);
            double spread = 1.28 * errorSpread * Math.sqrt(Math.max(p.value(), 1)); // ~80% range, wider for bigger dishes
            list.add(new DishForecast(d.getId(), d.getName(), d.getCategory(), d.getEmoji(),
                    Math.round(p.value() * 10) / 10.0, rec,
                    (int) Math.max(0, Math.floor(p.value() - spread)), (int) Math.ceil(p.value() + spread),
                    p.basis(), d.getDefaultStock()));
        }
        list.sort(Comparator.comparingDouble(DishForecast::predicted).reversed());
        int total = list.stream().mapToInt(DishForecast::recommendedPlates).sum();
        return new Forecast(target, h.openDays().size(), total, list, ingredients(list),
                "Same weekday over the last 4 weeks (40/30/20/10%) blended 60/40 with a 7-day weighted trend, +10% safety margin.");
    }

    /** Ingredient shopping list: recommended plates × recipe per plate, added up across dishes. */
    private List<IngredientNeed> ingredients(List<DishForecast> dishes) {
        Map<Long, Integer> plates = dishes.stream().filter(d -> d.recommendedPlates() > 0)
                .collect(Collectors.toMap(DishForecast::id, DishForecast::recommendedPlates));
        if (plates.isEmpty()) return List.of();
        Map<String, BigDecimal> need = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<String, String> units = new HashMap<>();
        for (DishIngredient i : recipes.findByMenuItemIdIn(plates.keySet())) {
            String key = i.getName().trim() + "|" + i.getUnit();
            need.merge(key, i.getPerPlate().multiply(BigDecimal.valueOf(plates.get(i.getMenuItemId()))), BigDecimal::add);
            units.put(key, i.getUnit());
        }
        return need.entrySet().stream()
                .map(e -> new IngredientNeed(e.getKey().substring(0, e.getKey().lastIndexOf('|')), units.get(e.getKey()),
                        e.getValue().setScale(e.getValue().compareTo(BigDecimal.TEN) >= 0 ? 1 : 2, RoundingMode.UP)))
                .toList();
    }

    /** Typical size of past forecast errors (per √plate), from replaying recent days. Used for the low–high range. */
    private double residualStd(History h, LocalDate target) {
        List<LocalDate> days = h.openDays().headSet(target).stream().sorted(Comparator.reverseOrder()).limit(14).toList();
        double sum = 0;
        int n = 0;
        for (LocalDate d : days) {
            for (Long dish : h.byDay().get(d).keySet()) {
                double p = predict(h, dish, d).value();
                double err = (h.qty(d, dish) - p) / Math.sqrt(Math.max(p, 1));
                sum += err * err;
                n++;
            }
        }
        return n < 5 ? 1.0 : Math.sqrt(sum / n);
    }

    // ---------------------------------------------------------------- accuracy (backtest)

    public record DayAccuracy(LocalDate date, int predicted, int actual) {}

    public record Accuracy(List<DayAccuracy> days, Double mape, Double accuracyPct, double mae, String note) {}

    /** For each of the last 14 open days: what we would have forecast that morning vs what actually sold. */
    @Transactional(readOnly = true)
    public Accuracy accuracy(Long canteenId) {
        LocalDate today = clock.today();
        History h = history(canteenId, today.minusDays(HISTORY_DAYS + 14), today.minusDays(1));
        List<LocalDate> days = h.openDays().stream().filter(d -> !d.isBefore(today.minusDays(14))).toList();
        Set<Long> dishes = menu.findAll().stream().filter(m -> canteenId.equals(m.getCanteenId()))
                .map(MenuItem::getId).collect(Collectors.toSet());
        List<DayAccuracy> out = new ArrayList<>();
        double absErr = 0, pctErr = 0;
        int pctDays = 0;
        for (LocalDate d : days) {
            double pred = dishes.stream().mapToDouble(id -> predict(h, id, d).value()).sum();
            int actual = h.total(d);
            out.add(new DayAccuracy(d, (int) Math.round(pred), actual));
            absErr += Math.abs(actual - pred);
            if (actual > 0) {
                pctErr += Math.abs(actual - pred) / actual;
                pctDays++;
            }
        }
        Double mape = pctDays == 0 ? null : Math.round(pctErr / pctDays * 1000) / 10.0;
        return new Accuracy(out, mape, mape == null ? null : Math.max(0, Math.round((100 - mape) * 10) / 10.0),
                days.isEmpty() ? 0 : Math.round(absErr / days.size() * 10) / 10.0,
                days.size() < 7 ? "Forecasts get more accurate after about 3–4 weeks of orders." : null);
    }

    // ---------------------------------------------------------------- peak hours

    public record Peaks(List<String> weekdays, List<Integer> hours, double[][] avgOrders, String busiest, int openDays) {}

    /** Average orders per hour for each weekday over the last 4 weeks. */
    @Transactional(readOnly = true)
    public Peaks peaks(Long canteenId) {
        LocalDate today = clock.today();
        History h = history(canteenId, today.minusDays(28), today.minusDays(1));
        List<Integer> hours = new ArrayList<>();
        for (int i = 7; i <= 20; i++) hours.add(i);
        double[][] grid = new double[7][hours.size()];
        int[] openPerDow = new int[7];
        h.openDays().forEach(d -> openPerDow[d.getDayOfWeek().getValue() - 1]++);
        for (Order o : h.raw()) {
            int dow = o.getOrderDate().getDayOfWeek().getValue() - 1;
            int hour = LocalDateTime.ofInstant(o.getCreatedAt(), clock.zone()).getHour();
            if (hour >= 7 && hour <= 20) grid[dow][hour - 7]++;
        }
        double best = -1;
        String busiest = null;
        for (int d = 0; d < 7; d++) {
            for (int i = 0; i < hours.size(); i++) {
                grid[d][i] = openPerDow[d] == 0 ? 0 : Math.round(grid[d][i] / openPerDow[d] * 10) / 10.0;
                if (grid[d][i] > best && grid[d][i] > 0) {
                    best = grid[d][i];
                    busiest = pretty(DayOfWeek.of(d + 1)) + "s " + hours.get(i) + ":00–" + (hours.get(i) + 1) + ":00";
                }
            }
        }
        List<String> names = Arrays.stream(DayOfWeek.values()).map(d -> pretty(d).substring(0, 3)).toList();
        return new Peaks(names, hours, grid, busiest, h.openDays().size());
    }

    // ---------------------------------------------------------------- best / least sellers

    public record Seller(Long id, String name, String emoji, int qty, BigDecimal revenue, int prevQty, Integer changePct) {}

    public record Sellers(List<Seller> best, List<Seller> least) {}

    /** Last 30 days vs the 30 before; "least" includes dishes that sold nothing. */
    @Transactional(readOnly = true)
    public Sellers sellers(Long canteenId) {
        LocalDate today = clock.today();
        List<Order> now = history(canteenId, today.minusDays(29), today).raw();
        List<Order> prev = history(canteenId, today.minusDays(59), today.minusDays(30)).raw();
        Map<Long, Integer> q = new HashMap<>(), pq = new HashMap<>();
        Map<Long, BigDecimal> rev = new HashMap<>();
        now.forEach(o -> o.getItems().forEach(i -> {
            q.merge(i.getMenuItemId(), i.getQuantity(), Integer::sum);
            rev.merge(i.getMenuItemId(), i.getLineTotal(), BigDecimal::add);
        }));
        prev.forEach(o -> o.getItems().forEach(i -> pq.merge(i.getMenuItemId(), i.getQuantity(), Integer::sum)));
        List<Seller> all = menu.findByCanteenIdAndArchivedFalseOrderByCategoryAscNameAsc(canteenId).stream().map(d -> {
            int a = q.getOrDefault(d.getId(), 0), b = pq.getOrDefault(d.getId(), 0);
            return new Seller(d.getId(), d.getName(), d.getEmoji(), a, rev.getOrDefault(d.getId(), BigDecimal.ZERO), b,
                    b == 0 ? null : (int) Math.round((a - b) * 100.0 / b));
        }).toList();
        return new Sellers(
                all.stream().filter(s -> s.qty() > 0).sorted(Comparator.comparingInt(Seller::qty).reversed()).limit(5).toList(),
                all.stream().sorted(Comparator.comparingInt(Seller::qty).thenComparing(Seller::name)).limit(5).toList());
    }

    private static String pretty(DayOfWeek d) {
        String n = d.name();
        return n.charAt(0) + n.substring(1).toLowerCase();
    }
}
