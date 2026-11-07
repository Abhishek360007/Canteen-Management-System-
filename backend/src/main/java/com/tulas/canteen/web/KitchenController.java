package com.tulas.canteen.web;

import com.tulas.canteen.dto.AdminDtos.CanteenView;
import com.tulas.canteen.dto.AdminDtos.DaySummary;
import com.tulas.canteen.dto.AdminDtos.StatusRequest;
import com.tulas.canteen.dto.MenuDtos.MenuItemView;
import com.tulas.canteen.dto.MenuDtos.StockUpdate;
import com.tulas.canteen.dto.OrderDtos.OrderView;
import com.tulas.canteen.dto.OrderDtos.StatusUpdate;
import com.tulas.canteen.dto.OrderDtos.VerifyRequest;
import com.tulas.canteen.service.*;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Counter and kitchen screens. STAFF and ADMIN only (see SecurityConfig). */
@RestController
@RequestMapping("/api/kitchen")
public class KitchenController {

    private final OrderService orders;
    private final MenuService menu;
    private final SettingsService settings;
    private final ReportService reports;
    private final Clock clock;

    public KitchenController(OrderService orders, MenuService menu, SettingsService settings,
                             ReportService reports, Clock clock) {
        this.orders = orders;
        this.menu = menu;
        this.settings = settings;
        this.reports = reports;
        this.clock = clock;
    }

    /** Orders still to make or hand over today, oldest first. */
    @GetMapping("/queue")
    public List<OrderView> queue() {
        return orders.kitchenQueue();
    }

    @GetMapping("/today")
    public List<OrderView> today() {
        return orders.today();
    }

    @PostMapping("/orders/{id}/status")
    public OrderView status(@PathVariable Long id, @Valid @RequestBody StatusUpdate r) {
        return orders.updateStatus(id, r.status(), r.reason());
    }

    @PostMapping("/verify")
    public ResponseEntity<OrderService.VerifyResult> verify(@Valid @RequestBody VerifyRequest r) {
        OrderService.VerifyResult res = orders.verify(r.code(), r.otp());
        return ResponseEntity.status(res.ok() ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_ENTITY).body(res);
    }

    @PutMapping("/menu/{id}/stock")
    public MenuItemView stock(@PathVariable Long id, @Valid @RequestBody StockUpdate r) {
        return menu.updateStock(id, r);
    }

    @PutMapping("/status")
    public CanteenView canteenStatus(@Valid @RequestBody StatusRequest r) {
        return settings.setStatus(r.status());
    }

    @GetMapping("/summary")
    public DaySummary summary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
        return reports.daySummary(day == null ? clock.today() : day);
    }
}
