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
    private final CanteenService canteens;
    private final ReportService reports;
    private final AuditService audit;
    private final Clock clock;

    public KitchenController(OrderService orders, MenuService menu, CanteenService canteens,
                             ReportService reports, AuditService audit, Clock clock) {
        this.orders = orders;
        this.menu = menu;
        this.canteens = canteens;
        this.reports = reports;
        this.audit = audit;
        this.clock = clock;
    }

    /** Orders still to make or hand over today, oldest first. */
    @GetMapping("/queue")
    public List<OrderView> queue(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) Long canteenId) {
        return orders.kitchenQueue(canteens.resolve(me, canteenId).getId());
    }

    @GetMapping("/today")
    public List<OrderView> today(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) Long canteenId) {
        return orders.today(canteens.resolve(me, canteenId).getId());
    }

    @PostMapping("/orders/{id}/status")
    public OrderView status(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @Valid @RequestBody StatusUpdate r) {
        OrderView o = orders.updateStatus(me, id, r.status(), r.reason());
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
    public ResponseEntity<OrderService.VerifyResult> verify(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) Long canteenId,
                                                           @Valid @RequestBody VerifyRequest r) {
        OrderService.VerifyResult res = orders.verify(canteens.resolve(me, canteenId).getId(), r.code(), r.otp());
        return ResponseEntity.status(res.ok() ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_ENTITY).body(res);
    }

    @PutMapping("/menu/{id}/stock")
    public MenuItemView stock(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @Valid @RequestBody StockUpdate r) {
        return menu.updateStock(me, id, r);
    }

    @PutMapping("/status")
    public CanteenView canteenStatus(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) Long canteenId, @Valid @RequestBody StatusRequest r) {
        CanteenView v = canteens.setStatus(canteens.resolve(me, canteenId).getId(), r.status());
        audit.record(me, "CANTEEN_STATUS", v.name(), "Set to " + r.status().name().toLowerCase());
        return v;
    }

    @GetMapping("/summary")
    public DaySummary summary(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) Long canteenId,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
        return reports.daySummary(day == null ? clock.today() : day, canteens.resolve(me, canteenId).getId());
    }
}
