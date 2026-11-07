package com.tulas.canteen.service;

import com.tulas.canteen.domain.DailyCounter;
import com.tulas.canteen.repo.DailyCounterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Creates the day's order-number counter in its own small transaction. If two orders are the first of the
 * day at the same moment, one insert loses with a duplicate key; that's fine, the row exists either way,
 * and the losing order's own transaction is untouched.
 */
@Service
public class CounterService {

    private final DailyCounterRepository counters;

    public CounterService(DailyCounterRepository counters) {
        this.counters = counters;
    }

    /** Throws DataIntegrityViolationException if another order created the row a moment ago; callers ignore it. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createIfMissing(LocalDate day) {
        if (!counters.existsById(day)) counters.saveAndFlush(new DailyCounter(day));
    }
}
