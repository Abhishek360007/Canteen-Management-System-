package com.tulas.canteen.service;

import com.tulas.canteen.domain.*;
import com.tulas.canteen.dto.AdminDtos.*;
import com.tulas.canteen.repo.OrderRepository;
import com.tulas.canteen.repo.TopupRequestRepository;
import com.tulas.canteen.repo.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReportService {

    /** Statuses that count as a sale. A no-show was cooked and paid for, so it counts too. */
    private static final Set<OrderStatus> SOLD = EnumSet.of(OrderStatus.PLACED, OrderStatus.PREPARING,
            OrderStatus.READY, OrderStatus.COLLECTED, OrderStatus.NO_SHOW);

    private final OrderRepository orders;
    private final UserRepository users;
    private final TopupRequestRepository topups;
    private final Clock clock;

    public ReportService(OrderRepository orders, UserRepository users, TopupRequestRepository topups, Clock clock) {
        this.orders = orders;
        this.users = users;
        this.topups = topups;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard() {
        LocalDate today = clock.today();
        List<Order> week = orders.findByOrderDateBetween(today.minusDays(6), today);
        List<Order> todays = week.stream().filter(o -> o.getOrderDate().equals(today)).toList();

        List<DayPoint> days = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            List<Order> sold = week.stream().filter(o -> o.getOrderDate().equals(d) && SOLD.contains(o.getStatus())).toList();
            days.add(new DayPoint(d, sold.size(), sum(sold)));
        }

        long[] hours = new long[24];
        week.forEach(o -> hours[LocalDateTime.ofInstant(o.getCreatedAt(), clock.zone()).getHour()]++);
        List<HourPoint> busy = new ArrayList<>();
        for (int h = 7; h <= 20; h++) busy.add(new HourPoint(h, hours[h]));

        double avgRating = week.stream().map(Order::getRating).filter(Objects::nonNull)
                .mapToInt(Integer::intValue).average().orElse(0);

        BigDecimal walletFloat = users.totalBalance(Role.STUDENT);

        return new Dashboard(
                todays.stream().filter(o -> SOLD.contains(o.getStatus())).count(),
                sum(todays.stream().filter(o -> SOLD.contains(o.getStatus())).toList()),
                todays.stream().filter(o -> o.getStatus().isActive()).count(),
                users.countByRole(Role.STUDENT),
                walletFloat,
                Math.round(avgRating * 10) / 10.0,
                count(todays, OrderStatus.NO_SHOW),
                count(todays, OrderStatus.CANCELLED),
                topups.countByStatus(TopupRequest.Status.PENDING),
                days,
                topItems(week, 5),
                busy);
    }

    /** End-of-day sheet for the counter: what was sold and how much cash should be in the drawer. */
    @Transactional(readOnly = true)
    public DaySummary daySummary(LocalDate day) {
        List<Order> list = orders.findByOrderDateBetween(day, day);
        List<Order> sold = list.stream().filter(o -> SOLD.contains(o.getStatus())).toList();
        BigDecimal wallet = sum(sold.stream().filter(o -> o.getPaymentMethod() == PaymentMethod.WALLET).toList());
        BigDecimal cashCollected = sum(list.stream()
                .filter(o -> o.getPaymentMethod() == PaymentMethod.CASH && o.getStatus() == OrderStatus.COLLECTED).toList());
        BigDecimal cashPending = sum(list.stream()
                .filter(o -> o.getPaymentMethod() == PaymentMethod.CASH && o.getStatus().isActive()).toList());
        return new DaySummary(day, sold.size(), count(list, OrderStatus.COLLECTED), count(list, OrderStatus.CANCELLED),
                count(list, OrderStatus.NO_SHOW), list.stream().filter(o -> o.getStatus().isActive()).count(),
                wallet, cashCollected, cashPending, topItems(list, 50));
    }

    @Transactional(readOnly = true)
    public SpendingView spending(Long userId) {
        YearMonth now = YearMonth.from(clock.today());
        List<Order> mine = orders.findTop50ByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(o -> SOLD.contains(o.getStatus())).toList();
        List<Order> thisMonth = mine.stream().filter(o -> YearMonth.from(o.getOrderDate()).equals(now)).toList();
        List<Order> lastMonth = mine.stream().filter(o -> YearMonth.from(o.getOrderDate()).equals(now.minusMonths(1))).toList();
        return new SpendingView(sum(thisMonth), sum(lastMonth), thisMonth.size(), topItems(mine, 3));
    }

    @Transactional(readOnly = true)
    public String ordersCsv(LocalDate from, LocalDate to, OrderStatus status) {
        DateTimeFormatter ts = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        StringBuilder sb = new StringBuilder("Order,Date,Time,Student,Roll No,Items,Payment,Total,Status\n");
        for (Order o : orders.findForReport(from, to, status)) {
            String items = o.getItems().stream().map(i -> i.getQuantity() + "x " + i.getName())
                    .collect(Collectors.joining("; "));
            sb.append(String.join(",",
                    csv(o.getCode()), csv(o.getOrderDate().toString()),
                    csv(LocalDateTime.ofInstant(o.getCreatedAt(), clock.zone()).format(ts).substring(11)),
                    csv(o.getUser().getName()), csv(o.getUser().getRollNo()), csv(items),
                    csv(o.getPaymentMethod().name()), o.getTotal().toPlainString(), csv(o.getStatus().name())))
              .append('\n');
        }
        return sb.toString();
    }

    private static List<NamedCount> topItems(List<Order> list, int limit) {
        Map<String, long[]> qty = new HashMap<>();
        Map<String, BigDecimal> rev = new HashMap<>();
        list.stream().filter(o -> SOLD.contains(o.getStatus())).flatMap(o -> o.getItems().stream()).forEach(i -> {
            qty.computeIfAbsent(i.getName(), k -> new long[1])[0] += i.getQuantity();
            rev.merge(i.getName(), i.getLineTotal(), BigDecimal::add);
        });
        return qty.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
                .limit(limit)
                .map(e -> new NamedCount(e.getKey(), e.getValue()[0], rev.get(e.getKey())))
                .toList();
    }

    private static BigDecimal sum(List<Order> list) {
        return list.stream().map(Order::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static long count(List<Order> list, OrderStatus s) {
        return list.stream().filter(o -> o.getStatus() == s).count();
    }

    /** Quotes a CSV cell, and defuses values Excel would run as a formula. */
    private static String csv(String v) {
        if (v == null) return "";
        String s = v;
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
