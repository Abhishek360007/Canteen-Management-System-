package com.tulas.canteen.service;

import com.tulas.canteen.config.ApiException;
import com.tulas.canteen.domain.MenuItem;
import com.tulas.canteen.domain.User;
import com.tulas.canteen.dto.MenuDtos.MenuItemRequest;
import com.tulas.canteen.dto.MenuDtos.MenuItemView;
import com.tulas.canteen.dto.MenuDtos.StockUpdate;
import com.tulas.canteen.repo.MenuItemRepository;
import com.tulas.canteen.repo.UserRepository;
import com.tulas.canteen.security.AuthUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class MenuService {

    private final MenuItemRepository menu;
    private final UserRepository users;
    private final LiveHub live;

    public MenuService(MenuItemRepository menu, UserRepository users, LiveHub live) {
        this.menu = menu;
        this.users = users;
        this.live = live;
    }

    /** Full menu (including items switched off today) with the viewer's favourites marked. */
    @Transactional(readOnly = true)
    public List<MenuItemView> list(AuthUser me) {
        Set<Long> favs = users.findById(me.id()).map(User::getFavouriteItemIds).map(Set::copyOf).orElse(Set.of());
        return menu.findByArchivedFalseOrderByCategoryAscNameAsc().stream()
                .map(m -> MenuItemView.of(m, favs.contains(m.getId())))
                .toList();
    }

    @Transactional
    public MenuItemView create(MenuItemRequest r) {
        MenuItem m = new MenuItem();
        apply(m, r);
        menu.save(m);
        changed();
        return MenuItemView.of(m, false);
    }

    @Transactional
    public MenuItemView update(Long id, MenuItemRequest r) {
        MenuItem m = load(id);
        apply(m, r);
        changed();
        return MenuItemView.of(m, false);
    }

    /** Archive instead of delete so past orders and ratings keep pointing at a real dish. */
    @Transactional
    public void archive(Long id) {
        MenuItem m = load(id);
        m.setArchived(true);
        m.setAvailable(false);
        changed();
    }

    @Transactional
    public MenuItemView updateStock(Long id, StockUpdate s) {
        MenuItem m = load(id);
        m.setStockToday(s.stockToday());
        m.setAvailable(s.available());
        changed();
        return MenuItemView.of(m, false);
    }

    @Transactional
    public boolean toggleFavourite(AuthUser me, Long itemId, boolean on) {
        load(itemId);
        User u = users.findById(me.id()).orElseThrow();
        if (on) u.getFavouriteItemIds().add(itemId);
        else u.getFavouriteItemIds().remove(itemId);
        return on;
    }

    private void apply(MenuItem m, MenuItemRequest r) {
        m.setName(r.name().trim());
        m.setDescription(r.description() == null || r.description().isBlank() ? null : r.description().trim());
        m.setPrice(r.price());
        m.setCategory(r.category());
        m.setVeg(r.veg());
        m.setEmoji(r.emoji() == null || r.emoji().isBlank() ? "🍽️" : r.emoji().trim());
        m.setImageUrl(r.imageUrl() == null || r.imageUrl().isBlank() ? null : r.imageUrl().trim());
        m.setAvailable(r.available());
        m.setStockToday(r.stockToday());
        m.setDefaultStock(r.defaultStock());
        m.setPrepMinutes(r.prepMinutes());
    }

    private MenuItem load(Long id) {
        return menu.findById(id).filter(m -> !m.isArchived()).orElseThrow(() -> ApiException.notFound("Dish not found"));
    }

    private void changed() {
        live.toEveryone("menu", Map.of("changed", true));
    }
}
