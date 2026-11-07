package com.tulas.canteen.web;

import com.tulas.canteen.dto.AdminDtos.SpendingView;
import com.tulas.canteen.dto.AdminDtos.TopupBody;
import com.tulas.canteen.dto.AdminDtos.TopupView;
import com.tulas.canteen.service.TopupService;
import com.tulas.canteen.dto.AdminDtos.WalletView;
import com.tulas.canteen.dto.OrderDtos.OrderView;
import com.tulas.canteen.dto.OrderDtos.PlaceOrderRequest;
import com.tulas.canteen.dto.OrderDtos.RateRequest;
import com.tulas.canteen.security.AuthUser;
import com.tulas.canteen.service.MenuService;
import com.tulas.canteen.service.OrderService;
import com.tulas.canteen.service.ReportService;
import com.tulas.canteen.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class StudentController {

    private final OrderService orders;
    private final WalletService wallet;
    private final MenuService menu;
    private final ReportService reports;
    private final TopupService topups;

    public StudentController(OrderService orders, WalletService wallet, MenuService menu, ReportService reports,
                             TopupService topups) {
        this.orders = orders;
        this.wallet = wallet;
        this.menu = menu;
        this.reports = reports;
        this.topups = topups;
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderView place(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody PlaceOrderRequest r,
                           @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return orders.place(me, r, key);
    }

    @GetMapping("/orders")
    public List<OrderView> mine(@AuthenticationPrincipal AuthUser me) {
        return orders.myOrders(me);
    }

    @GetMapping("/orders/{id}")
    public OrderView one(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return orders.get(me, id);
    }

    @PostMapping("/orders/{id}/cancel")
    public OrderView cancel(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return orders.cancelByStudent(me, id);
    }

    @PostMapping("/orders/{id}/rate")
    public OrderView rate(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @Valid @RequestBody RateRequest r) {
        return orders.rate(me, id, r.stars());
    }

    @GetMapping("/wallet")
    public WalletView wallet(@AuthenticationPrincipal AuthUser me) {
        return wallet.view(me.id());
    }

    @GetMapping("/wallet/spending")
    public SpendingView spending(@AuthenticationPrincipal AuthUser me) {
        return reports.spending(me.id());
    }

    /** Ask for a UPI payment to be added to the wallet. */
    @PostMapping("/wallet/topups")
    @ResponseStatus(HttpStatus.CREATED)
    public TopupView requestTopup(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody TopupBody r) {
        return topups.request(me, r.amount(), r.upiRef());
    }

    @GetMapping("/wallet/topups")
    public List<TopupView> myTopups(@AuthenticationPrincipal AuthUser me) {
        return topups.mine(me.id());
    }

    @PutMapping("/favourites/{itemId}")
    public Map<String, Boolean> addFavourite(@AuthenticationPrincipal AuthUser me, @PathVariable Long itemId) {
        return Map.of("favourite", menu.toggleFavourite(me, itemId, true));
    }

    @DeleteMapping("/favourites/{itemId}")
    public Map<String, Boolean> removeFavourite(@AuthenticationPrincipal AuthUser me, @PathVariable Long itemId) {
        return Map.of("favourite", menu.toggleFavourite(me, itemId, false));
    }
}
