package com.tulas.canteen.web;

import com.tulas.canteen.dto.AdminDtos.CanteenView;
import com.tulas.canteen.dto.AdminDtos.DaySummary;
import com.tulas.canteen.dto.AdminDtos.StatusRequest;
import com.tulas.canteen.dto.MenuDtos.MenuItemView;
import com.tulas.canteen.dto.MenuDtos.StockUpdate;
import com.tulas.canteen.dto.OrderDtos.OrderView;
import com.tulas.canteen.dto.OrderDtos.StatusUpdate;
import com.tulas.canteen.dto.OrderDtos.VerifyRequest;
import com.tulas.canteen.domain.PaymentMethod;
import com.tulas.canteen.security.AuthUser;
import com.tulas.canteen.service.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    private final AuditService audit;
    private final Clock clock;

    public KitchenController(OrderService orders, MenuService menu, SettingsService settings,
                             ReportService reports, AuditService audit, Clock clock) {
        this.orders = orders;
        this.menu = menu;
        this.settings = settings;
        this.reports = reports;
        this.audit = audit;
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
    public OrderView status(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @Valid @RequestBody StatusUpdate r) {
        OrderView o = orders.updateStatus(id, r.status(), r.reason());
        // Routine cooking steps aren't logged; money-affecting or unusual ones are.
        switch (r.status()) {
            case CANCELLED -> audit.record(me, "ORDER_CANCELLED", "Order " + o.code() + " · " + o.customerName(),
                    rupees(o) + (o.paymentMethod() == PaymentMethod.WALLET ? " refunded" : "") + " · " + o.cancelReason());
            case COLLECTED -> audit.record(me, "ORDER_MANUAL_HANDOVER", "Order " + o.code() + " · " + o.customerName(),
                    "Handed over without the pickup code · " + rupees(o));
            case NO_SHOW -> audit.record(me, "ORDER_NO_SHOW", "Order " + o.code() + " · " + o.customerName(), rupees(o));
            default -> { }
        }
        return o;
    }

    private static String rupees(OrderView o) {
        return AuditService.rupees(o.total());
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
    public CanteenView canteenStatus(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody StatusRequest r) {
        CanteenView v = settings.setStatus(r.status());
        audit.record(me, "CANTEEN_STATUS", "Canteen", "Set to " + r.status().name().toLowerCase());
        return v;
    }

    @GetMapping("/summary")
    public DaySummary summary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
        return reports.daySummary(day == null ? clock.today() : day);
    }
}
