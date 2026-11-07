package com.tulas.canteen.service;

import com.tulas.canteen.domain.OrderCounter;
import com.tulas.canteen.repo.OrderCounterRepository;
import com.tulas.canteen.repo.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Creates a canteen's order-number counter for the day in its own small transaction. If two orders are the
 * first of the day at the same moment, one insert loses with a duplicate key; that's fine, the row exists
 * either way, and the losing order's own transaction is untouched.
 * <p>
 * A new counter starts after the highest number already used that day (e.g. orders placed before an upgrade
 * that introduced this counter), so numbers never clash.
 */
@Service
public class CounterService {

    private final OrderCounterRepository counters;
    private final OrderRepository orders;

    public CounterService(OrderCounterRepository counters, OrderRepository orders) {
        this.counters = counters;
        this.orders = orders;
    }

    /** Throws DataIntegrityViolationException if another order created the row a moment ago; callers ignore it. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createIfMissing(LocalDate day, Long canteenId, String prefix) {
        String key = OrderCounter.key(day, canteenId);
        if (counters.existsById(key)) return;
        int highest = orders.findCodesOn(day, prefix + "-%").stream()
                .map(code -> code.substring(code.indexOf('-') + 1))
                .filter(n -> n.chars().allMatch(Character::isDigit) && !n.isEmpty())
                .mapToInt(Integer::parseInt).max().orElse(0);
        counters.saveAndFlush(new OrderCounter(key, highest));
    }
}
