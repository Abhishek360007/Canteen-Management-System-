package com.tulas.canteen.web;

import com.tulas.canteen.config.ApiException;
import com.tulas.canteen.domain.Announcement;
import com.tulas.canteen.domain.OrderStatus;
import com.tulas.canteen.domain.Role;
import com.tulas.canteen.domain.TopupRequest;
import com.tulas.canteen.domain.User;
import com.tulas.canteen.dto.AdminDtos.*;
import com.tulas.canteen.dto.AuthDtos.UserView;
import com.tulas.canteen.dto.MenuDtos.MenuItemRequest;
import com.tulas.canteen.dto.MenuDtos.MenuItemView;
import com.tulas.canteen.dto.OrderDtos.OrderView;
import com.tulas.canteen.repo.AnnouncementRepository;
import com.tulas.canteen.repo.AuditLogRepository;
import com.tulas.canteen.repo.OrderRepository;
import com.tulas.canteen.repo.UserRepository;
import com.tulas.canteen.security.AuthUser;
import com.tulas.canteen.service.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Admin panel. ADMIN only (see SecurityConfig). Actions that change money, users or the menu are logged. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final ReportService reports;
    private final UserRepository users;
    private final OrderRepository orders;
    private final AnnouncementRepository announcements;
    private final AuditLogRepository auditLogs;
    private final MenuService menu;
    private final WalletService wallet;
    private final AuthService auth;
    private final SettingsService settings;
    private final TopupService topups;
    private final AuditService audit;
    private final LiveHub live;
    private final Clock clock;

    public AdminController(ReportService reports, UserRepository users, OrderRepository orders,
                           AnnouncementRepository announcements, AuditLogRepository auditLogs, MenuService menu,
                           WalletService wallet, AuthService auth, SettingsService settings, TopupService topups,
                           AuditService audit, LiveHub live, Clock clock) {
        this.reports = reports;
        this.users = users;
        this.orders = orders;
        this.announcements = announcements;
        this.auditLogs = auditLogs;
        this.menu = menu;
        this.wallet = wallet;
        this.auth = auth;
        this.settings = settings;
        this.topups = topups;
        this.audit = audit;
        this.live = live;
        this.clock = clock;
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard() {
        return reports.dashboard();
    }

    // ---- users

    @GetMapping("/users")
    @Transactional(readOnly = true)
    public Map<String, Object> users(@RequestParam(required = false) String q,
                                     @RequestParam(required = false) Role role,
                                     @RequestParam(defaultValue = "0") int page) {
        String query = q == null || q.isBlank() ? null : q.trim();
        Page<User> p = users.search(query, role, PageRequest.of(Math.max(page, 0), 25));
        return Map.of("items", p.getContent().stream().map(UserView::of).toList(),
                "page", p.getNumber(), "totalPages", p.getTotalPages(), "total", p.getTotalElements());
    }

    @PostMapping("/users/{id}/recharge")
    public TxnView recharge(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                            @Valid @RequestBody RechargeRequest r) {
        TxnView t = wallet.recharge(id, r.amount(), r.note(), me.name());
        audit.record(me, "WALLET_RECHARGE", AuditService.who(auth.get(id)),
                AuditService.rupees(r.amount()) + (r.note() == null || r.note().isBlank() ? "" : " · " + r.note().trim()));
        return t;
    }

    @GetMapping("/users/{id}/wallet")
    public WalletView userWallet(@PathVariable Long id) {
        return wallet.view(id);
    }

    @PostMapping("/users/{id}/block")
    @Transactional
    public UserView block(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @RequestBody BlockRequest r) {
        if (me.id().equals(id)) throw ApiException.badRequest("You can't block your own account.");
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));
        u.setBlocked(r.blocked());
        audit.record(me, r.blocked() ? "USER_BLOCKED" : "USER_UNBLOCKED", AuditService.who(u), null);
        return UserView.of(u);
    }

    /** For a user who forgot their password: sets a temporary one and logs them out everywhere. */
    @PostMapping("/users/{id}/reset-password")
    public TempPasswordView resetPassword(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        if (me.id().equals(id)) throw ApiException.badRequest("Change your own password under Account.");
        String temp = auth.resetPassword(id);
        audit.record(me, "PASSWORD_RESET", AuditService.who(auth.get(id)), "Temporary password issued");
        return new TempPasswordView(temp);
    }

    /** E.g. a student lost their phone: every device they are logged in on is signed out. */
    @PostMapping("/users/{id}/logout-everywhere")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logoutEverywhere(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        auth.logoutEverywhere(id);
        audit.record(me, "USER_LOGGED_OUT", AuditService.who(auth.get(id)), "Signed out on all devices");
    }

    @PostMapping("/staff")
    @ResponseStatus(HttpStatus.CREATED)
    public UserView createStaff(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody CreateStaffRequest r) {
        User u = auth.createUser(r.name(), r.email(), r.password(), r.phone(), Role.STAFF);
        audit.record(me, "STAFF_CREATED", AuditService.who(u), null);
        return UserView.of(u);
    }

    // ---- UPI top-ups

    @GetMapping("/topups")
    public List<TopupView> topups(@RequestParam(required = false) TopupRequest.Status status) {
        return topups.list(status);
    }

    @PostMapping("/topups/{id}/approve")
    public TopupView approveTopup(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return topups.approve(me, id);
    }

    @PostMapping("/topups/{id}/reject")
    public TopupView rejectTopup(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                 @Valid @RequestBody(required = false) RejectBody r) {
        return topups.reject(me, id, r == null ? null : r.reason());
    }

    // ---- menu

    @PostMapping("/menu")
    @ResponseStatus(HttpStatus.CREATED)
    public MenuItemView createDish(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody MenuItemRequest r) {
        MenuItemView v = menu.create(r);
        audit.record(me, "DISH_ADDED", v.name(), AuditService.rupees(v.price()));
        return v;
    }

    @PutMapping("/menu/{id}")
    public MenuItemView updateDish(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                                   @Valid @RequestBody MenuItemRequest r) {
        MenuItemView v = menu.update(id, r);
        audit.record(me, "DISH_UPDATED", v.name(), AuditService.rupees(v.price())
                + (v.available() ? "" : " · off today"));
        return v;
    }

    @DeleteMapping("/menu/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDish(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        String name = menu.nameOf(id);
        menu.archive(id);
        audit.record(me, "DISH_REMOVED", name, null);
    }

    /** One click to fill an empty menu with a standard canteen menu (dishes already present are skipped). */
    @PostMapping("/menu/import-defaults")
    public ImportResult importDefaultMenu(@AuthenticationPrincipal AuthUser me) {
        int[] r = menu.importDefaults();
        if (r[0] > 0) audit.record(me, "MENU_IMPORTED", "Standard menu", r[0] + " dishes added");
        return new ImportResult(r[0], r[1]);
    }

    // ---- orders

    @GetMapping("/orders")
    @Transactional(readOnly = true)
    public List<OrderView> orders(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                  @RequestParam(required = false) OrderStatus status) {
        LocalDate[] range = range(from, to);
        return orders.findForReport(range[0], range[1], status).stream().limit(500).map(OrderView::staff).toList();
    }

    @GetMapping("/orders.csv")
    public ResponseEntity<byte[]> ordersCsv(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                            @RequestParam(required = false) OrderStatus status) {
        LocalDate[] range = range(from, to);
        // BOM so Excel opens the ₹/Unicode text correctly.
        byte[] body = ("﻿" + reports.ordersCsv(range[0], range[1], status)).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"tulas-orders-" + range[0] + "-to-" + range[1] + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    private LocalDate[] range(LocalDate from, LocalDate to) {
        LocalDate t = to == null ? clock.today() : to;
        LocalDate f = from == null ? t.minusDays(6) : from;
        if (f.isAfter(t)) throw ApiException.badRequest("'From' date must be before 'to' date.");
        if (f.plusDays(366).isBefore(t)) throw ApiException.badRequest("Pick a range of one year or less.");
        return new LocalDate[]{f, t};
    }

    // ---- activity log

    @GetMapping("/audit")
    @Transactional(readOnly = true)
    public Map<String, Object> auditLog(@RequestParam(required = false) String q,
                                        @RequestParam(defaultValue = "0") int page) {
        String query = q == null || q.isBlank() ? null : q.trim();
        var p = auditLogs.search(query, PageRequest.of(Math.max(page, 0), 50));
        return Map.of("items", p.getContent().stream().map(AuditView::of).toList(),
                "page", p.getNumber(), "totalPages", p.getTotalPages());
    }

    // ---- announcements

    @GetMapping("/announcements")
    public List<AnnouncementView> allAnnouncements() {
        return announcements.findAllByOrderByCreatedAtDesc().stream().map(AnnouncementView::of).toList();
    }

    @PostMapping("/announcements")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public AnnouncementView createAnnouncement(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody AnnouncementRequest r) {
        Announcement a = new Announcement();
        a.setMessage(r.message().trim());
        a.setActive(r.active());
        announcements.save(a);
        live.toEveryone("announcement", Map.of("changed", true));
        audit.record(me, "ANNOUNCEMENT_POSTED", null, a.getMessage());
        return AnnouncementView.of(a);
    }

    @PutMapping("/announcements/{id}")
    @Transactional
    public AnnouncementView updateAnnouncement(@PathVariable Long id, @Valid @RequestBody AnnouncementRequest r) {
        Announcement a = announcements.findById(id).orElseThrow(() -> ApiException.notFound("Announcement not found"));
        a.setMessage(r.message().trim());
        a.setActive(r.active());
        live.toEveryone("announcement", Map.of("changed", true));
        return AnnouncementView.of(a);
    }

    @DeleteMapping("/announcements/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deleteAnnouncement(@PathVariable Long id) {
        announcements.deleteById(id);
        live.toEveryone("announcement", Map.of("changed", true));
    }

    // ---- settings

    @GetMapping("/settings")
    public CanteenView getSettings() {
        return settings.view();
    }

    @PutMapping("/settings")
    public CanteenView updateSettings(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody SettingsRequest r) {
        CanteenView v = settings.update(r);
        audit.record(me, "SETTINGS_CHANGED", "Canteen settings",
                "Hours " + r.openTime() + "–" + r.closeTime() + " · cash " + (r.cashAllowed() ? "on" : "off")
                        + " · UPI " + (v.upiId() == null ? "off" : v.upiId()));
        return v;
    }
}
