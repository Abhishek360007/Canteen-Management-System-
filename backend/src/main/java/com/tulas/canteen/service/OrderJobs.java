package com.tulas.canteen.service;

import com.tulas.canteen.repo.MenuItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/** Background routines that keep the day's data tidy without anyone having to remember. */
@Component
public class OrderJobs {

    private static final Logger log = LoggerFactory.getLogger(OrderJobs.class);

    private final OrderService orders;
    private final MenuItemRepository menu;
    private final LiveHub live;

    public OrderJobs(OrderService orders, MenuItemRepository menu, LiveHub live) {
        this.orders = orders;
        this.menu = menu;
        this.live = live;
    }

    /** READY orders not collected within the hold time become no-shows; their plates go back on sale. */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void noShows() {
        int n = orders.markNoShows();
        if (n > 0) log.info("Marked {} order(s) as no-show", n);
    }

    /** Orders from earlier days that never finished are cancelled and refunded. */
    @Scheduled(fixedDelay = 600_000, initialDelay = 45_000)
    public void staleOrders() {
        int n = orders.cancelStale();
        if (n > 0) log.info("Cancelled {} unfinished order(s) from earlier days", n);
    }

    /** Every morning, each dish's plates-left count is reset to its usual daily amount. */
    @Scheduled(cron = "0 0 5 * * *", zone = "${app.zone}")
    @Transactional
    public void morningReset() {
        int n = menu.resetDailyStock();
        live.toEveryone("menu", Map.of("changed", true));
        log.info("Morning reset: stock restored for {} dish(es)", n);
    }
}
