package com.tulas.canteen.config;

import com.tulas.canteen.domain.Announcement;
import com.tulas.canteen.domain.Category;
import com.tulas.canteen.domain.MenuItem;
import com.tulas.canteen.domain.Role;
import com.tulas.canteen.domain.User;
import com.tulas.canteen.repo.AnnouncementRepository;
import com.tulas.canteen.repo.MenuItemRepository;
import com.tulas.canteen.repo.UserRepository;
import com.tulas.canteen.service.AuthService;
import com.tulas.canteen.service.SettingsService;
import com.tulas.canteen.service.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Creates the admin account on first start. With app.seed-demo=true (the default for local runs)
 * it also adds a demo menu, a kitchen login and a student with some wallet money.
 * Set SEED_DEMO=false in production.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository users;
    private final MenuItemRepository menu;
    private final AnnouncementRepository announcements;
    private final AuthService auth;
    private final WalletService wallet;
    private final SettingsService settings;

    @Value("${app.admin.email}") private String adminEmail;
    @Value("${app.admin.password}") private String adminPassword;
    @Value("${app.admin.name}") private String adminName;
    @Value("${app.seed-demo}") private boolean seedDemo;

    public DataSeeder(UserRepository users, MenuItemRepository menu, AnnouncementRepository announcements,
                      AuthService auth, WalletService wallet, SettingsService settings) {
        this.users = users;
        this.menu = menu;
        this.announcements = announcements;
        this.auth = auth;
        this.wallet = wallet;
        this.settings = settings;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        settings.get();
        if (!users.existsByEmailIgnoreCase(adminEmail)) {
            auth.createUser(adminName, adminEmail, adminPassword, null, Role.ADMIN);
            log.info("Created admin account {}", adminEmail);
        }
        if (!seedDemo || menu.count() > 0) return;

        auth.createUser("Kitchen Counter", "kitchen@tulas.local", "kitchen123", null, Role.STAFF);
        User student = auth.createUser("Demo Student", "student@tulas.local", "student123", "9876543210", Role.STUDENT);
        student.setRollNo("TU-CSE-001");
        wallet.recharge(student.getId(), new BigDecimal("500.00"), "Welcome balance", "system");

        dish("Masala Dosa", "Crispy dosa with potato masala, sambar and chutney", "60", Category.BREAKFAST, true, "🥞", 40, 8);
        dish("Idli Vada", "2 idlis and 1 vada with sambar", "45", Category.BREAKFAST, true, "🍚", 50, 5);
        dish("Poha", "Flattened rice with peanuts and onion", "30", Category.BREAKFAST, true, "🍛", null, 5);
        dish("Veg Thali", "2 rotis, rice, dal, sabzi, salad and papad", "90", Category.MEALS, true, "🍱", 60, 10);
        dish("Chicken Biryani", "Dum biryani with raita and salan", "140", Category.MEALS, false, "🍗", 35, 15);
        dish("Paneer Butter Masala + 2 Roti", "Rich tomato gravy with soft paneer", "110", Category.MEALS, true, "🧀", 30, 12);
        dish("Rajma Chawal", "Kidney bean curry with steamed rice", "70", Category.MEALS, true, "🍛", 40, 8);
        dish("Samosa (2 pcs)", "Served with green and tamarind chutney", "25", Category.SNACKS, true, "🥟", 80, 3);
        dish("Veg Sandwich", "Grilled sandwich with cheese", "50", Category.SNACKS, true, "🥪", null, 6);
        dish("Egg Roll", "Paratha roll with egg and onions", "55", Category.SNACKS, false, "🌯", 40, 7);
        dish("Masala Chai", "Kadak ginger tea", "15", Category.BEVERAGES, true, "☕", null, 2);
        dish("Cold Coffee", "Thick blended cold coffee", "50", Category.BEVERAGES, true, "🥤", null, 4);
        dish("Fresh Lime Soda", "Sweet or salted", "30", Category.BEVERAGES, true, "🍋", null, 3);
        dish("Gulab Jamun (2 pcs)", "Warm, soaked in syrup", "35", Category.DESSERTS, true, "🍮", 50, 2);

        Announcement a = new Announcement();
        a.setMessage("Welcome to Tula's Canteen! Order ahead, skip the queue, and collect with your pickup code.");
        announcements.save(a);

        log.info("Demo data added: student@tulas.local / student123, kitchen@tulas.local / kitchen123");
    }

    private void dish(String name, String desc, String price, Category cat, boolean veg, String emoji,
                      Integer stock, int prep) {
        MenuItem m = new MenuItem();
        m.setName(name);
        m.setDescription(desc);
        m.setPrice(new BigDecimal(price));
        m.setCategory(cat);
        m.setVeg(veg);
        m.setEmoji(emoji);
        m.setStockToday(stock);
        m.setDefaultStock(stock);
        m.setPrepMinutes(prep);
        menu.save(m);
    }
}
