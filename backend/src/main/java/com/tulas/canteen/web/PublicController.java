package com.tulas.canteen.web;

import com.tulas.canteen.dto.AdminDtos.AnnouncementView;
import com.tulas.canteen.dto.AdminDtos.CanteenView;
import com.tulas.canteen.dto.MenuDtos.MenuItemView;
import com.tulas.canteen.repo.AnnouncementRepository;
import com.tulas.canteen.security.AuthUser;
import com.tulas.canteen.service.LiveHub;
import com.tulas.canteen.service.MenuService;
import com.tulas.canteen.service.SettingsService;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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

    public PublicController(MenuService menu, SettingsService settings, AnnouncementRepository announcements, LiveHub live) {
        this.menu = menu;
        this.settings = settings;
        this.announcements = announcements;
        this.live = live;
    }

    @GetMapping("/menu")
    public List<MenuItemView> menu(@AuthenticationPrincipal AuthUser me) {
        return menu.list(me);
    }

    @GetMapping("/canteen")
    public CanteenView canteen() {
        return settings.view();
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
