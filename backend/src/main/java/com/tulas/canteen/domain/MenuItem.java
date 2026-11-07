package com.tulas.canteen.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;

/** {@code @DynamicUpdate}: saving a rating can't overwrite plates-left changed by a concurrent order. */
@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(name = "menu_items")
public class MenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 240)
    private String description;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Category category;

    @Column(nullable = false)
    private boolean veg = true;

    /** Shown on the dish tile when there is no photo. */
    @Column(length = 8)
    private String emoji;

    @Column(length = 500)
    private String imageUrl;

    /** On today's menu. Staff can switch a dish off when it runs out or isn't made today. */
    @Column(nullable = false)
    private boolean available = true;

    /** Plates left today. null means no limit (made to order). */
    private Integer stockToday;

    /** Plates restored every morning by the daily reset. null means no limit. */
    private Integer defaultStock;

    @Column(nullable = false)
    private int prepMinutes = 10;

    /** Soft delete: archived items are hidden everywhere but keep old order history intact. */
    @Column(nullable = false)
    private boolean archived = false;

    @Column(nullable = false)
    private int ratingSum = 0;

    @Column(nullable = false)
    private int ratingCount = 0;

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public boolean isVeg() { return veg; }
    public void setVeg(boolean veg) { this.veg = veg; }
    public String getEmoji() { return emoji; }
    public void setEmoji(String emoji) { this.emoji = emoji; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
    public Integer getStockToday() { return stockToday; }
    public void setStockToday(Integer stockToday) { this.stockToday = stockToday; }
    public Integer getDefaultStock() { return defaultStock; }
    public void setDefaultStock(Integer defaultStock) { this.defaultStock = defaultStock; }
    public int getPrepMinutes() { return prepMinutes; }
    public void setPrepMinutes(int prepMinutes) { this.prepMinutes = prepMinutes; }
    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }
    public int getRatingSum() { return ratingSum; }
    public int getRatingCount() { return ratingCount; }

    public void addRating(int stars) {
        ratingSum += stars;
        ratingCount++;
    }

    public Double getAverageRating() {
        return ratingCount == 0 ? null : Math.round(ratingSum * 10.0 / ratingCount) / 10.0;
    }
}
