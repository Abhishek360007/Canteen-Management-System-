package com.tulas.canteen.web;

import com.tulas.canteen.config.ApiException;
import com.tulas.canteen.domain.Announcement;
import com.tulas.canteen.domain.OrderStatus;
import com.tulas.canteen.domain.Role;
import com.tulas.canteen.domain.User;
import com.tulas.canteen.dto.AdminDtos.*;
import com.tulas.canteen.dto.AuthDtos.UserView;
import com.tulas.canteen.dto.MenuDtos.MenuItemRequest;
import com.tulas.canteen.dto.MenuDtos.MenuItemView;
import com.tulas.canteen.dto.OrderDtos.OrderView;
import com.tulas.canteen.repo.AnnouncementRepository;
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

/** Admin panel. ADMIN only (see SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final ReportService reports;
    private final UserRepository users;
    private final OrderRepository orders;
    private final AnnouncementRepository announcements;
    private final MenuService menu;
    private final WalletService wallet;
    private final AuthService auth;
    private final SettingsService settings;
    private final LiveHub live;
    private final Clock clock;

    public AdminController(ReportService reports, UserRepository users, OrderRepository orders,
                           AnnouncementRepository announcements, MenuService menu, WalletService wallet,
                           AuthService auth, SettingsService settings, LiveHub live, Clock clock) {
        this.reports = reports;
        this.users = users;
        this.orders = orders;
        this.announcements = announcements;
        this.menu = menu;
        this.wallet = wallet;
        this.auth = auth;
        this.settings = settings;
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
        return wallet.recharge(id, r.amount(), r.note(), me.name());
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
        return UserView.of(u);
    }

    @PostMapping("/staff")
    @ResponseStatus(HttpStatus.CREATED)
    public UserView createStaff(@Valid @RequestBody CreateStaffRequest r) {
        return UserView.of(auth.createUser(r.name(), r.email(), r.password(), r.phone(), Role.STAFF));
    }

    // ---- menu

    @PostMapping("/menu")
    @ResponseStatus(HttpStatus.CREATED)
    public MenuItemView createDish(@Valid @RequestBody MenuItemRequest r) {
        return menu.create(r);
    }

    @PutMapping("/menu/{id}")
    public MenuItemView updateDish(@PathVariable Long id, @Valid @RequestBody MenuItemRequest r) {
        return menu.update(id, r);
    }

    @DeleteMapping("/menu/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDish(@PathVariable Long id) {
        menu.archive(id);
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

    // ---- announcements

    @GetMapping("/announcements")
    public List<AnnouncementView> allAnnouncements() {
        return announcements.findAllByOrderByCreatedAtDesc().stream().map(AnnouncementView::of).toList();
    }

    @PostMapping("/announcements")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public AnnouncementView createAnnouncement(@Valid @RequestBody AnnouncementRequest r) {
        Announcement a = new Announcement();
        a.setMessage(r.message().trim());
        a.setActive(r.active());
        announcements.save(a);
        live.toEveryone("announcement", Map.of("changed", true));
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
    public CanteenView updateSettings(@Valid @RequestBody SettingsRequest r) {
        return settings.update(r);
    }
}
