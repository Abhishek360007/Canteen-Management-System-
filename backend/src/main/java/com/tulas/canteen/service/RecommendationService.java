package com.tulas.canteen.service;

import com.tulas.canteen.domain.*;
import com.tulas.canteen.repo.MenuItemRepository;
import com.tulas.canteen.repo.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dish recommendations from real order data, explained in plain words.
 *
 * <p><b>Basket analysis (association rules).</b> From the last 60 days of orders we count how often each dish
 * appears and how often two dishes appear in the same order. For dishes A and B:
 * <ul>
 *   <li>confidence(A→B) = orders with A and B / orders with A ("people who buy A also buy B x% of the time")</li>
 *   <li>lift(A→B) = confidence(A→B) / share of all orders with B (above 1 means "more than chance")</li>
 * </ul>
 * <b>Personal taste.</b> A student's own orders (recent ones count more) give their favourite dishes,
 * favourite categories and whether they eat only veg. <b>Context.</b> What's selling this week and the time of day.
 * The model is rebuilt at most every 10 minutes.
 */
@Service
public class RecommendationService {

    private static final Set<OrderStatus> SOLD = EnumSet.of(OrderStatus.PLACED, OrderStatus.PREPARING,
            OrderStatus.READY, OrderStatus.COLLECTED, OrderStatus.NO_SHOW);
    private static final Duration MODEL_TTL = Duration.ofMinutes(10);
    /** A pair must be seen together this often before we trust it. */
    private static final int MIN_PAIR_SUPPORT = 2;
    private static final double HALF_LIFE_DAYS = 14;

    public record Rec(Long menuItemId, String reason) {}

    public record Feed(List<Rec> forYou, List<Rec> orderAgain, List<Rec> trending, boolean vegOnly) {}

    private record Model(Instant builtAt, int orders, Map<Long, Integer> itemOrders,
                         Map<Long, Map<Long, Integer>> pairs, Map<Long, Integer> qty7d) {

        double confidence(long a, long b) {
            int na = itemOrders.getOrDefault(a, 0);
            int nab = pairs.getOrDefault(a, Map.of()).getOrDefault(b, 0);
            return na == 0 || nab < MIN_PAIR_SUPPORT ? 0 : (double) nab / na;
        }

        double lift(long a, long b) {
            double c = confidence(a, b);
            int nb = itemOrders.getOrDefault(b, 0);
            return c == 0 || nb == 0 ? 0 : c / ((double) nb / orders);
        }
    }

    private final OrderRepository orders;
    private final MenuItemRepository menu;
    private final Clock clock;
    private volatile Model model;

    public RecommendationService(OrderRepository orders, MenuItemRepository menu, Clock clock) {
        this.orders = orders;
        this.menu = menu;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- feeds

    @Transactional(readOnly = true)
    public Feed feed(Long userId) {
        Model m = model();
        Map<Long, MenuItem> live = orderable();
        Map<Long, MenuItem> all = new HashMap<>();
        menu.findAll().forEach(d -> all.put(d.getId(), d));
        List<Order> mine = orders.findTop50ByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(o -> SOLD.contains(o.getStatus())).toList();

        // Personal taste: recency-weighted dish frequency, category affinity, veg share.
        Map<Long, Double> taste = new HashMap<>();
        Map<Long, Integer> timesOrdered = new HashMap<>();
        Map<Category, Double> catAffinity = new EnumMap<>(Category.class);
        double vegQty = 0, allQty = 0, tasteTotal = 0;
        LocalDate today = clock.today();
        for (Order o : mine) {
            double w = Math.pow(0.5, Math.max(0, today.toEpochDay() - o.getOrderDate().toEpochDay()) / HALF_LIFE_DAYS);
            for (OrderItem i : o.getItems()) {
                taste.merge(i.getMenuItemId(), w, Double::sum);
                timesOrdered.merge(i.getMenuItemId(), 1, Integer::sum);
                tasteTotal += w;
                MenuItem d = all.get(i.getMenuItemId());
                if (d != null) {
                    catAffinity.merge(d.getCategory(), w, Double::sum);
                    allQty += i.getQuantity();
                    if (d.isVeg()) vegQty += i.getQuantity();
                }
            }
        }
        boolean vegOnly = mine.size() >= 3 && allQty > 0 && vegQty / allQty >= 0.9;
        final double tasteSum = tasteTotal == 0 ? 1 : tasteTotal;

        // Order again: your most frequent dishes that are available now.
        List<Rec> again = timesOrdered.entrySet().stream()
                .filter(e -> live.containsKey(e.getKey()))
                .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed()
                        .thenComparing(e -> -taste.getOrDefault(e.getKey(), 0.0)))
                .limit(5)
                .map(e -> new Rec(e.getKey(), e.getValue() == 1 ? "You ordered this before"
                        : "You've ordered this " + e.getValue() + " times"))
                .toList();

        List<Rec> trending = trending(m, live, vegOnly);

        // For you: new dishes that go with what you like, in categories you like, popular right now, right for the time.
        int maxQty = m.qty7d().values().stream().max(Integer::compare).orElse(1);
        Category timeCat = categoryForNow();
        Set<Long> alreadyAgain = again.stream().map(Rec::menuItemId).collect(Collectors.toSet());
        List<Scored> scored = new ArrayList<>();
        for (MenuItem d : live.values()) {
            if (vegOnly && !d.isVeg()) continue;
            if (alreadyAgain.contains(d.getId())) continue;
            double assoc = 0;
            String assocWith = null;
            for (var t : taste.entrySet()) {
                if (t.getKey().equals(d.getId())) continue;
                double s = (t.getValue() / tasteSum) * m.confidence(t.getKey(), d.getId()) * Math.max(0, m.lift(t.getKey(), d.getId()) - 1);
                if (s > assoc) {
                    assoc = s;
                    MenuItem src = live.get(t.getKey());
                    assocWith = src == null ? null : src.getName();
                }
            }
            double cat = catAffinity.getOrDefault(d.getCategory(), 0.0) / tasteSum;
            double pop = (double) m.qty7d().getOrDefault(d.getId(), 0) / maxQty;
            double time = d.getCategory() == timeCat ? 1 : 0;
            double rating = d.getAverageRating() == null ? 0 : (d.getAverageRating() - 3) / 2; // -1..1

            double score = 3.0 * assoc + 1.0 * cat + 0.8 * pop + 0.5 * time + 0.3 * rating;
            String reason;
            if (assocWith != null && assoc > 0.02) reason = "Goes well with " + assocWith;
            else if (cat >= 0.3) reason = "Because you like " + label(d.getCategory());
            else if (pop >= 0.5) reason = "Popular this week";
            else if (time > 0) reason = timeReason(timeCat);
            else if (rating > 0.5) reason = "Highly rated";
            else reason = "Try something new";
            scored.add(new Scored(d.getId(), score, reason));
        }
        List<Rec> forYou = scored.stream().sorted(Comparator.comparingDouble(Scored::score).reversed())
                .limit(6).map(s -> new Rec(s.id(), s.reason())).toList();
        return new Feed(forYou, again, trending, vegOnly);
    }

    /** "Goes well with your cart": strongest basket rules from the cart, then a drink / dessert if missing. */
    @Transactional(readOnly = true)
    public List<Rec> forCart(Collection<Long> cartIds) {
        if (cartIds.isEmpty()) return List.of();
        Model m = model();
        Map<Long, MenuItem> live = orderable();
        Set<Long> cart = new HashSet<>(cartIds);
        List<MenuItem> cartDishes = menu.findAllById(cart);
        boolean allVeg = !cartDishes.isEmpty() && cartDishes.stream().allMatch(MenuItem::isVeg);
        Set<Category> cartCats = cartDishes.stream().map(MenuItem::getCategory)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Category.class)));

        Map<Long, Scored> best = new LinkedHashMap<>();
        for (MenuItem d : live.values()) {
            if (cart.contains(d.getId()) || (allVeg && !d.isVeg())) continue;
            for (MenuItem c : cartDishes) {
                double conf = m.confidence(c.getId(), d.getId());
                if (conf > 0 && m.lift(c.getId(), d.getId()) > 1) {
                    Scored s = new Scored(d.getId(), conf, Math.round(conf * 100) + "% of people who order " + c.getName() + " add this");
                    best.merge(d.getId(), s, (a, b) -> a.score() >= b.score() ? a : b);
                }
            }
        }
        List<Rec> out = new ArrayList<>(best.values().stream().sorted(Comparator.comparingDouble(Scored::score).reversed())
                .limit(3).map(s -> new Rec(s.id(), s.reason())).toList());

        // Not enough history yet: suggest the most popular drink / dessert the cart (or a suggestion) doesn't have.
        out.forEach(r -> { MenuItem d = live.get(r.menuItemId()); if (d != null) cartCats.add(d.getCategory()); });
        if (out.size() < 3 && !cartCats.contains(Category.BEVERAGES)) {
            topIn(Category.BEVERAGES, live, m, allVeg, cart, out).ifPresent(id -> out.add(new Rec(id, "Add a drink?")));
        }
        if (out.size() < 3 && !cartCats.contains(Category.DESSERTS)) {
            topIn(Category.DESSERTS, live, m, allVeg, cart, out).ifPresent(id -> out.add(new Rec(id, "Something sweet to finish?")));
        }
        return out;
    }

    /** Alternatives for a dish (e.g. sold out): same category and veg type, closest price, best rated. */
    @Transactional(readOnly = true)
    public List<Rec> similar(Long dishId) {
        MenuItem src = menu.findById(dishId).orElse(null);
        if (src == null) return List.of();
        return orderable().values().stream()
                .filter(d -> !d.getId().equals(dishId) && d.getCategory() == src.getCategory())
                .filter(d -> !src.isVeg() || d.isVeg())
                .sorted(Comparator.comparingDouble((MenuItem d) -> Math.abs(d.getPrice().doubleValue() - src.getPrice().doubleValue()))
                        .thenComparing(d -> -(d.getAverageRating() == null ? 0 : d.getAverageRating())))
                .limit(3)
                .map(d -> new Rec(d.getId(), "Instead of " + src.getName()))
                .toList();
    }

    // ---------------------------------------------------------------- helpers

    private record Scored(Long id, double score, String reason) {}

    private List<Rec> trending(Model m, Map<Long, MenuItem> live, boolean vegOnly) {
        List<Rec> list = m.qty7d().entrySet().stream()
                .filter(e -> live.containsKey(e.getKey()) && (!vegOnly || live.get(e.getKey()).isVeg()))
                .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                .limit(6)
                .map(e -> new Rec(e.getKey(), e.getValue() + " ordered this week"))
                .toList();
        if (!list.isEmpty()) return list;
        // No sales yet: best-rated, then what suits the time of day.
        Category c = categoryForNow();
        return live.values().stream().filter(d -> !vegOnly || d.isVeg())
                .sorted(Comparator.comparing((MenuItem d) -> d.getCategory() != c)
                        .thenComparing(d -> -(d.getAverageRating() == null ? 0 : d.getAverageRating())))
                .limit(6).map(d -> new Rec(d.getId(), timeReason(c))).toList();
    }

    private Optional<Long> topIn(Category cat, Map<Long, MenuItem> live, Model m, boolean vegOnly, Set<Long> cart, List<Rec> already) {
        Set<Long> taken = already.stream().map(Rec::menuItemId).collect(Collectors.toSet());
        return live.values().stream()
                .filter(d -> d.getCategory() == cat && !cart.contains(d.getId()) && !taken.contains(d.getId()) && (!vegOnly || d.isVeg()))
                .max(Comparator.comparingInt((MenuItem d) -> m.qty7d().getOrDefault(d.getId(), 0))
                        .thenComparing(d -> d.getAverageRating() == null ? 0 : d.getAverageRating()))
                .map(MenuItem::getId);
    }

    /** Dishes a student can order right now. */
    private Map<Long, MenuItem> orderable() {
        Map<Long, MenuItem> map = new LinkedHashMap<>();
        for (MenuItem d : menu.findByArchivedFalseOrderByCategoryAscNameAsc()) {
            boolean soldOut = d.getStockToday() != null && d.getStockToday() <= 0;
            if (d.isAvailable() && !soldOut) map.put(d.getId(), d);
        }
        return map;
    }

    private Category categoryForNow() {
        int h = clock.now().getHour();
        return h < 11 ? Category.BREAKFAST : h < 15 ? Category.MEALS : h < 19 ? Category.SNACKS : Category.MEALS;
    }

    private static String timeReason(Category c) {
        return switch (c) {
            case BREAKFAST -> "Good for breakfast";
            case MEALS -> "Good for lunch";
            case SNACKS -> "Evening snack";
            default -> "Popular choice";
        };
    }

    private static String label(Category c) {
        return switch (c) {
            case BREAKFAST -> "breakfast dishes";
            case MEALS -> "meals";
            case SNACKS -> "snacks";
            case BEVERAGES -> "drinks";
            case DESSERTS -> "desserts";
        };
    }

    private Model model() {
        Model m = model;
        if (m != null && Duration.between(m.builtAt(), Instant.now()).compareTo(MODEL_TTL) < 0) return m;
        synchronized (this) {
            if (model != null && Duration.between(model.builtAt(), Instant.now()).compareTo(MODEL_TTL) < 0) return model;
            model = build();
            return model;
        }
    }

    /** Count dishes and dish pairs per order over the last 60 days, and quantities over the last 7. */
    private Model build() {
        LocalDate today = clock.today();
        List<Order> recent = orders.findByOrderDateBetween(today.minusDays(60), today).stream()
                .filter(o -> SOLD.contains(o.getStatus())).toList();
        Map<Long, Integer> itemOrders = new HashMap<>();
        Map<Long, Map<Long, Integer>> pairs = new HashMap<>();
        Map<Long, Integer> qty7d = new HashMap<>();
        for (Order o : recent) {
            Set<Long> ids = o.getItems().stream().map(OrderItem::getMenuItemId).collect(Collectors.toSet());
            for (Long a : ids) {
                itemOrders.merge(a, 1, Integer::sum);
                for (Long b : ids) if (!a.equals(b)) pairs.computeIfAbsent(a, k -> new HashMap<>()).merge(b, 1, Integer::sum);
            }
            if (!o.getOrderDate().isBefore(today.minusDays(6))) {
                o.getItems().forEach(i -> qty7d.merge(i.getMenuItemId(), i.getQuantity(), Integer::sum));
            }
        }
        return new Model(Instant.now(), Math.max(1, recent.size()), itemOrders, pairs, qty7d);
    }

    /** For tests: forget the cached model so new orders are seen immediately. */
    public void refresh() {
        model = null;
    }
}
