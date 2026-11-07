package com.tulas.canteen.config;

import com.tulas.canteen.domain.*;
import com.tulas.canteen.repo.MenuItemRepository;
import com.tulas.canteen.repo.OrderRepository;
import com.tulas.canteen.service.AuthService;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/**
 * Demo mode only: four weeks of believable past sales so forecasts, peak hours, best sellers and
 * recommendations have something to show on a fresh install. Never runs in production.
 * Patterns: breakfast in the morning, meals at lunch, snacks in the evening; Saturdays half as busy,
 * Sundays closed; dishes bought in natural pairs (dosa + filter coffee, thali + buttermilk, samosa + chai).
 */
final class DemoHistory {

    private DemoHistory() {}

    private record Basket(String[] dishes, double perDay, int hourFrom, int hourTo) {}

    private static final List<Basket> BASKETS = List.of(
            new Basket(new String[]{"Masala Dosa", "Filter Coffee"}, 9, 8, 10),
            new Basket(new String[]{"Idli Sambar (2 pcs)", "Filter Coffee"}, 7, 8, 10),
            new Basket(new String[]{"Poha", "Masala Chai"}, 6, 8, 10),
            new Basket(new String[]{"Aloo Paratha"}, 4, 8, 11),
            new Basket(new String[]{"Veg Thali", "Buttermilk"}, 14, 12, 14),
            new Basket(new String[]{"Rajma Chawal"}, 6, 12, 14),
            new Basket(new String[]{"Chicken Biryani", "Fresh Lime Soda"}, 7, 12, 14),
            new Basket(new String[]{"Veg Biryani"}, 4, 12, 14),
            new Basket(new String[]{"Paneer Butter Masala + 2 Roti", "Sweet Lassi"}, 4, 12, 14),
            new Basket(new String[]{"Samosa (2 pcs)", "Masala Chai"}, 12, 16, 18),
            new Basket(new String[]{"Vada Pav", "Masala Chai"}, 8, 16, 18),
            new Basket(new String[]{"Pav Bhaji", "Cold Coffee"}, 5, 16, 18),
            new Basket(new String[]{"Masala Maggi"}, 5, 16, 19),
            new Basket(new String[]{"Gulab Jamun (2 pcs)"}, 3, 13, 15)
    );

    static void generate(OrderRepository orders, MenuItemRepository menu, AuthService auth, ZoneId zone, Canteen canteen) {
        Map<String, MenuItem> byName = new HashMap<>();
        menu.findByCanteenIdAndArchivedFalseOrderByCategoryAscNameAsc(canteen.getId()).forEach(m -> byName.put(m.getName(), m));
        Random rnd = new Random(42);

        List<User> students = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            students.add(auth.createUser("Demo Diner " + i, "diner" + i + "@tulas.local",
                    UUID.randomUUID().toString(), null, Role.STUDENT));
        }

        LocalDate today = LocalDate.now(zone);
        for (int back = 28; back >= 1; back--) {
            LocalDate day = today.minusDays(back);
            if (day.getDayOfWeek() == DayOfWeek.SUNDAY) continue; // closed
            double dayFactor = switch (day.getDayOfWeek()) {
                case MONDAY -> 1.15;
                case FRIDAY -> 1.1;
                case SATURDAY -> 0.5;
                default -> 1.0;
            };
            int n = 0;
            for (Basket b : BASKETS) {
                int count = (int) Math.max(0, Math.round(b.perDay() * dayFactor * (0.8 + rnd.nextDouble() * 0.4)));
                for (int k = 0; k < count; k++) {
                    Order o = new Order();
                    o.setUser(students.get(rnd.nextInt(students.size())));
                    o.setCanteen(canteen);
                    o.setCode(canteen.getCode() + String.format("-%03d", ++n));
                    o.setOrderDate(day);
                    o.setPaymentMethod(rnd.nextBoolean() ? PaymentMethod.WALLET : PaymentMethod.CASH);
                    o.setOtp("0000");
                    o.setStatus(OrderStatus.COLLECTED);
                    BigDecimal total = BigDecimal.ZERO;
                    for (String name : b.dishes()) {
                        MenuItem m = byName.get(name);
                        if (m == null) continue;
                        o.addItem(new OrderItem(m, 1));
                        total = total.add(m.getPrice());
                    }
                    if (o.getItems().isEmpty()) continue;
                    o.setTotal(total);
                    LocalTime t = LocalTime.of(b.hourFrom() + rnd.nextInt(b.hourTo() - b.hourFrom()), rnd.nextInt(60));
                    o.setCreatedAt(day.atTime(t).atZone(zone).toInstant());
                    orders.save(o);
                }
            }
        }
    }
}
