package com.tulas.canteen.web;

import com.tulas.canteen.dto.AdminDtos.SpendingView;
import com.tulas.canteen.dto.AdminDtos.TopupBody;
import com.tulas.canteen.dto.AdminDtos.TopupView;
import com.tulas.canteen.service.LoyaltyService;
import com.tulas.canteen.service.RecommendationService;
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
    private final LoyaltyService loyalty;
    private final RecommendationService recommender;
    private final com.tulas.canteen.service.CanteenService canteens;

    public StudentController(OrderService orders, WalletService wallet, MenuService menu, ReportService reports,
                             TopupService topups, LoyaltyService loyalty, RecommendationService recommender,
                             com.tulas.canteen.service.CanteenService canteens) {
        this.canteens = canteens;
        this.loyalty = loyalty;
        this.recommender = recommender;
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

    // ---- rewards

    @GetMapping("/rewards")
    public LoyaltyService.Summary rewards(@AuthenticationPrincipal AuthUser me) {
        return loyalty.summary(me.id());
    }

    public record BirthdayBody(@jakarta.validation.constraints.NotNull java.time.LocalDate birthday) {}

    @PutMapping("/rewards/birthday")
    public LoyaltyService.Summary setBirthday(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody BirthdayBody b) {
        loyalty.setBirthday(me.id(), b.birthday());
        return loyalty.summary(me.id());
    }

    // ---- recommendations

    @GetMapping("/recommendations")
    public RecommendationService.Feed recommendations(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) Long canteenId) {
        return recommender.feed(me.id(), canteens.resolve(me, canteenId).getId());
    }

    public record CartBody(@jakarta.validation.constraints.Size(max = 30) List<Long> itemIds) {}

    @PostMapping("/recommendations/cart")
    public List<RecommendationService.Rec> forCart(@Valid @RequestBody CartBody b) {
        return recommender.forCart(b.itemIds() == null ? List.of() : b.itemIds());
    }

    @GetMapping("/recommendations/similar/{id}")
    public List<RecommendationService.Rec> similar(@PathVariable Long id) {
        return recommender.similar(id);
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
