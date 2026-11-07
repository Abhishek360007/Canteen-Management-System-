package com.tulas.canteen.service;

import com.tulas.canteen.domain.Category;
import com.tulas.canteen.domain.MenuItem;

import java.math.BigDecimal;
import java.util.List;

import static com.tulas.canteen.domain.Category.*;

/**
 * A standard Indian college-canteen menu with everyday prices. Used for demo mode and for the
 * admin's "Add standard menu" button. Admins can edit prices and stock afterwards.
 * {@code stock} is plates per day (null = made to order, no limit).
 */
public final class DefaultMenu {

    private DefaultMenu() {}

    public record Dish(String name, String description, int price, Category category, boolean veg,
                       String emoji, Integer stock, int prepMinutes) {

        public MenuItem toMenuItem() {
            MenuItem m = new MenuItem();
            m.setName(name);
            m.setDescription(description);
            m.setPrice(BigDecimal.valueOf(price));
            m.setCategory(category);
            m.setVeg(veg);
            m.setEmoji(emoji);
            m.setStockToday(stock);
            m.setDefaultStock(stock);
            m.setPrepMinutes(prepMinutes);
            return m;
        }
    }

    public static final List<Dish> DISHES = List.of(
            // Breakfast
            new Dish("Masala Dosa", "Crispy dosa with potato masala, sambar and chutney", 60, BREAKFAST, true, "🥞", 40, 8),
            new Dish("Plain Dosa", "Crispy dosa with sambar and coconut chutney", 45, BREAKFAST, true, "🥞", 40, 6),
            new Dish("Idli Sambar (2 pcs)", "Soft steamed idlis with sambar and chutney", 35, BREAKFAST, true, "🍚", 60, 4),
            new Dish("Medu Vada (2 pcs)", "Crispy lentil vadas with sambar and chutney", 40, BREAKFAST, true, "🍩", 50, 5),
            new Dish("Poha", "Flattened rice with peanuts, onion and lemon", 30, BREAKFAST, true, "🍛", null, 5),
            new Dish("Upma", "Semolina cooked with vegetables and curry leaves", 30, BREAKFAST, true, "🍲", null, 5),
            new Dish("Aloo Paratha", "Stuffed potato paratha with curd and pickle", 50, BREAKFAST, true, "🫓", 40, 8),
            new Dish("Chole Bhature", "Spicy chickpea curry with 2 fluffy bhature", 70, BREAKFAST, true, "🥘", 30, 10),
            new Dish("Puri Bhaji", "4 puris with potato bhaji", 45, BREAKFAST, true, "🥔", 40, 7),
            new Dish("Bread Omelette", "2-egg masala omelette with toasted bread", 40, BREAKFAST, false, "🍳", null, 6),

            // Meals
            new Dish("Veg Thali", "2 rotis, rice, dal, sabzi, salad and papad", 90, MEALS, true, "🍱", 60, 10),
            new Dish("Rajma Chawal", "Kidney bean curry with steamed rice", 70, MEALS, true, "🍛", 40, 8),
            new Dish("Dal Tadka + Jeera Rice", "Yellow dal tempered with garlic, with cumin rice", 75, MEALS, true, "🍛", 40, 8),
            new Dish("Chole Rice", "Punjabi chickpea curry with steamed rice", 65, MEALS, true, "🍛", 40, 8),
            new Dish("Paneer Butter Masala + 2 Roti", "Rich tomato gravy with soft paneer", 110, MEALS, true, "🧀", 30, 12),
            new Dish("Veg Biryani", "Fragrant basmati rice with vegetables, with raita", 100, MEALS, true, "🍚", 35, 12),
            new Dish("Chicken Biryani", "Dum biryani with raita and salan", 140, MEALS, false, "🍗", 35, 15),
            new Dish("Egg Curry + Rice", "2 boiled eggs in onion-tomato gravy with rice", 80, MEALS, false, "🥚", 30, 10),
            new Dish("Veg Fried Rice", "Wok-tossed rice with vegetables", 70, MEALS, true, "🍳", null, 8),
            new Dish("Veg Hakka Noodles", "Indo-Chinese noodles with vegetables", 70, MEALS, true, "🍜", null, 8),
            new Dish("Curd Rice", "Cool curd rice with tempering and pickle", 50, MEALS, true, "🍚", 30, 4),

            // Snacks
            new Dish("Samosa (2 pcs)", "Served with green and tamarind chutney", 25, SNACKS, true, "🥟", 80, 3),
            new Dish("Vada Pav", "Spicy potato vada in a pav with chutneys", 20, SNACKS, true, "🍔", 80, 3),
            new Dish("Pav Bhaji", "Buttery mashed vegetable bhaji with 2 pavs", 60, SNACKS, true, "🍛", 40, 8),
            new Dish("Veg Sandwich", "Grilled sandwich with cheese", 50, SNACKS, true, "🥪", null, 6),
            new Dish("Veg Burger", "Crispy veg patty, cheese and sauces", 55, SNACKS, true, "🍔", null, 7),
            new Dish("French Fries", "Salted crispy fries with ketchup", 60, SNACKS, true, "🍟", null, 6),
            new Dish("Veg Momos (6 pcs)", "Steamed momos with spicy red chutney", 60, SNACKS, true, "🥟", 40, 8),
            new Dish("Paneer Roll", "Paratha roll with spiced paneer tikka", 70, SNACKS, true, "🌯", 30, 8),
            new Dish("Egg Roll", "Paratha roll with egg and onions", 55, SNACKS, false, "🌯", 40, 7),
            new Dish("Masala Maggi", "Maggi noodles cooked with vegetables and masala", 40, SNACKS, true, "🍜", null, 5),

            // Beverages
            new Dish("Masala Chai", "Kadak ginger tea", 15, BEVERAGES, true, "☕", null, 2),
            new Dish("Filter Coffee", "South Indian filter coffee", 20, BEVERAGES, true, "☕", null, 2),
            new Dish("Cold Coffee", "Thick blended cold coffee", 50, BEVERAGES, true, "🥤", null, 4),
            new Dish("Fresh Lime Soda", "Sweet or salted", 30, BEVERAGES, true, "🍋", null, 3),
            new Dish("Mango Lassi", "Thick mango and curd shake", 45, BEVERAGES, true, "🥭", null, 3),
            new Dish("Sweet Lassi", "Chilled sweet curd drink", 35, BEVERAGES, true, "🥛", null, 3),
            new Dish("Buttermilk", "Spiced chaas with mint", 20, BEVERAGES, true, "🥛", null, 2),
            new Dish("Mineral Water (1 L)", "Sealed bottle", 20, BEVERAGES, true, "💧", null, 1),

            // Desserts
            new Dish("Gulab Jamun (2 pcs)", "Warm, soaked in syrup", 35, DESSERTS, true, "🍮", 50, 2),
            new Dish("Rasgulla (2 pcs)", "Soft chhena balls in light syrup", 35, DESSERTS, true, "⚪", 50, 2),
            new Dish("Rice Kheer", "Creamy rice pudding with cardamom", 40, DESSERTS, true, "🍮", 30, 2),
            new Dish("Ice Cream Cup", "Vanilla, chocolate or strawberry", 30, DESSERTS, true, "🍨", null, 1)
    );
}
