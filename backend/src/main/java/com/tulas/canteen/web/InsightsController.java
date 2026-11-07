package com.tulas.canteen.web;

import com.tulas.canteen.config.ApiException;
import com.tulas.canteen.domain.MenuItem;
import com.tulas.canteen.repo.MenuItemRepository;
import com.tulas.canteen.security.AuthUser;
import com.tulas.canteen.service.AuditService;
import com.tulas.canteen.service.Clock;
import com.tulas.canteen.service.ForecastService;
import com.tulas.canteen.service.MenuService;
import com.tulas.canteen.service.MenuService.RecipeLine;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Forecasts and sales insights. Kitchen staff can read the forecast (to plan cooking);
 * the rest, and applying a forecast to stock, is admin-only.
 */
@RestController
public class InsightsController {

    private final ForecastService forecasts;
    private final MenuService menu;
    private final MenuItemRepository dishes;
    private final AuditService audit;
    private final Clock clock;

    public InsightsController(ForecastService forecasts, MenuService menu, MenuItemRepository dishes,
                              AuditService audit, Clock clock) {
        this.forecasts = forecasts;
        this.menu = menu;
        this.dishes = dishes;
        this.audit = audit;
        this.clock = clock;
    }

    private LocalDate target(LocalDate date) {
        LocalDate d = date == null ? clock.today().plusDays(1) : date;
        if (d.isBefore(clock.today()) || d.isAfter(clock.today().plusDays(14))) {
            throw ApiException.badRequest("Pick a date from today up to two weeks ahead.");
        }
        return d;
    }

    @GetMapping({"/api/kitchen/forecast", "/api/admin/insights/forecast"})
    public ForecastService.Forecast forecast(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return forecasts.forecast(target(date));
    }

    @GetMapping("/api/admin/insights/accuracy")
    public ForecastService.Accuracy accuracy() {
        return forecasts.accuracy();
    }

    @GetMapping("/api/admin/insights/peaks")
    public ForecastService.Peaks peaks() {
        return forecasts.peaks();
    }

    @GetMapping("/api/admin/insights/sellers")
    public ForecastService.Sellers sellers() {
        return forecasts.sellers();
    }

    /**
     * Use the forecast as the daily plates for dishes that have a plate limit; the 5 AM reset then uses them.
     * Dishes made to order (no limit) are left alone. Never sets fewer than 5 plates.
     */
    @PostMapping("/api/admin/insights/forecast/apply")
    @Transactional
    public Map<String, Integer> applyForecast(@AuthenticationPrincipal AuthUser me,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        ForecastService.Forecast f = forecasts.forecast(target(date));
        int changed = 0;
        for (ForecastService.DishForecast d : f.dishes()) {
            MenuItem m = dishes.findById(d.id()).orElse(null);
            if (m == null || m.getDefaultStock() == null) continue;
            int plates = Math.max(5, d.recommendedPlates());
            if (m.getDefaultStock() != plates) {
                m.setDefaultStock(plates);
                changed++;
            }
        }
        audit.record(me, "FORECAST_APPLIED", "Daily plates", changed + " dishes set from the forecast for " + f.date());
        return Map.of("changed", changed);
    }

    // ---- recipes (per-plate ingredients, used for the ingredient forecast)

    @GetMapping("/api/admin/menu/{id}/recipe")
    public List<MenuService.RecipeLine> recipe(@PathVariable Long id) {
        return menu.recipe(id);
    }

    @PutMapping("/api/admin/menu/{id}/recipe")
    public List<MenuService.RecipeLine> setRecipe(@PathVariable Long id, @Valid @RequestBody List<@Valid RecipeLine> lines) {
        if (lines.size() > 20) throw ApiException.badRequest("A recipe can have at most 20 ingredients.");
        return menu.setRecipe(id, lines);
    }
}
