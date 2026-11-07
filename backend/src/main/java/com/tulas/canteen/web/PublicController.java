package com.tulas.canteen.web;

import com.tulas.canteen.dto.AdminDtos.AnnouncementView;
import com.tulas.canteen.dto.AdminDtos.CanteenView;
import com.tulas.canteen.dto.MenuDtos.MenuItemView;
import com.tulas.canteen.repo.AnnouncementRepository;
import com.tulas.canteen.security.AuthUser;
import com.tulas.canteen.service.CanteenService;
import com.tulas.canteen.service.LiveHub;
import com.tulas.canteen.service.MenuService;
import com.tulas.canteen.service.SettingsService;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/** Endpoints for any logged-in user. */
@RestController
@RequestMapping("/api")
public class PublicController {

    private final MenuService menu;
    private final SettingsService settings;
    private final AnnouncementRepository announcements;
    private final LiveHub live;
    private final CanteenService canteens;

    public PublicController(MenuService menu, SettingsService settings, AnnouncementRepository announcements, LiveHub live,
                            CanteenService canteens) {
        this.canteens = canteens;
        this.menu = menu;
        this.settings = settings;
        this.announcements = announcements;
        this.live = live;
    }

    /** Used by the hosting platform to check the app is up. Touches the database so a dead DB shows as down. */
    @GetMapping("/health")
    public java.util.Map<String, String> health() {
        settings.view();
        // Render sets RENDER_GIT_COMMIT, so you can see which commit is live.
        String commit = System.getenv("RENDER_GIT_COMMIT");
        return java.util.Map.of("status", "ok",
                "version", commit == null || commit.length() < 7 ? "local" : commit.substring(0, 7));
    }

    /** The menu of one canteen (?canteenId=, default the first; kitchen staff always get their own). */
    @GetMapping("/menu")
    public List<MenuItemView> menu(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) Long canteenId) {
        return menu.list(me, canteens.resolve(me, canteenId).getId());
    }

    /** All canteens students can order from, with live open/closed status. */
    @GetMapping("/canteens")
    public List<CanteenView> canteens() {
        return canteens.list(false);
    }

    @GetMapping("/canteen")
    public CanteenView canteen(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) Long canteenId) {
        return canteens.view(canteens.resolve(me, canteenId));
    }

    @GetMapping("/announcements")
    public List<AnnouncementView> announcements() {
        return announcements.findByActiveTrueOrderByCreatedAtDesc().stream().map(AnnouncementView::of).toList();
    }

    /** Live updates stream (Server-Sent Events). The browser passes ?token=... since EventSource can't set headers. */
    @GetMapping(value = "/live", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter live(@AuthenticationPrincipal AuthUser me) {
        return live.connect(me);
    }
}
