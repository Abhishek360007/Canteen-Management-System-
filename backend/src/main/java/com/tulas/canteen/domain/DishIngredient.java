package com.tulas.canteen.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;

/** One line of a dish's recipe: how much of an ingredient one plate uses (e.g. Veg Thali → Rice 0.15 kg). */
@Entity
@Table(name = "dish_ingredients", indexes = @Index(columnList = "menu_item_id"))
public class DishIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "menu_item_id", nullable = false)
    private Long menuItemId;

    @Column(nullable = false, length = 60)
    private String name;

    /** kg, L, pcs … */
    @Column(nullable = false, length = 10)
    private String unit;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal perPlate;

    public DishIngredient() {}

    public DishIngredient(Long menuItemId, String name, String unit, BigDecimal perPlate) {
        this.menuItemId = menuItemId;
        this.name = name;
        this.unit = unit;
        this.perPlate = perPlate;
    }

    public Long getId() { return id; }
    public Long getMenuItemId() { return menuItemId; }
    public String getName() { return name; }
    public String getUnit() { return unit; }
    public BigDecimal getPerPlate() { return perPlate; }
}
