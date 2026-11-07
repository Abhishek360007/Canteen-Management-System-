package com.tulas.canteen.dto;

import com.tulas.canteen.domain.Category;
import com.tulas.canteen.domain.MenuItem;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public final class MenuDtos {

    private MenuDtos() {}

    public record MenuItemView(Long id, String name, String description, BigDecimal price, Category category,
                               boolean veg, String emoji, String imageUrl, boolean available, Integer stockToday,
                               Integer defaultStock, int prepMinutes, Double rating, int ratingCount,
                               boolean soldOut, boolean favourite) {
        public static MenuItemView of(MenuItem m, boolean favourite) {
            boolean soldOut = m.getStockToday() != null && m.getStockToday() <= 0;
            return new MenuItemView(m.getId(), m.getName(), m.getDescription(), m.getPrice(), m.getCategory(),
                    m.isVeg(), m.getEmoji(), m.getImageUrl(), m.isAvailable(), m.getStockToday(),
                    m.getDefaultStock(), m.getPrepMinutes(), m.getAverageRating(), m.getRatingCount(),
                    soldOut, favourite);
        }
    }

    public record MenuItemRequest(
            @NotBlank @Size(max = 80) String name,
            @Size(max = 240) String description,
            @NotNull @DecimalMin("1.00") @DecimalMax("5000.00") BigDecimal price,
            @NotNull Category category,
            boolean veg,
            @Size(max = 8) String emoji,
            @Size(max = 500) @Pattern(regexp = "^$|^https?://.+", message = "must be an http(s) link") String imageUrl,
            boolean available,
            @Min(0) @Max(9999) Integer stockToday,
            @Min(0) @Max(9999) Integer defaultStock,
            @Min(1) @Max(120) int prepMinutes) {}

    /** Kitchen quick-edit: plates left and on/off for today. stockToday null = unlimited. */
    public record StockUpdate(@Min(0) @Max(9999) Integer stockToday, boolean available) {}
}
