package com.tulas.canteen.config;

import com.tulas.canteen.domain.Announcement;
import com.tulas.canteen.domain.Role;
import com.tulas.canteen.domain.User;
import com.tulas.canteen.repo.AnnouncementRepository;
import com.tulas.canteen.repo.MenuItemRepository;
import com.tulas.canteen.repo.UserRepository;
import com.tulas.canteen.service.AuthService;
import com.tulas.canteen.service.DefaultMenu;
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
import java.util.List;
import java.security.SecureRandom;

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
    private final com.tulas.canteen.service.MenuService menuService;
    @org.springframework.beans.factory.annotation.Autowired private com.tulas.canteen.service.CanteenService canteens;

    @Value("${app.admin.email}") private String adminEmail;
    @Value("${app.admin.password}") private String adminPassword;
    @Value("${app.admin.name}") private String adminName;
    @Value("${app.seed-demo}") private boolean seedDemo;
    /** Four weeks of sample sales for the forecast pages (demo mode only). */
    @Value("${app.seed-demo-history:false}") private boolean demoHistory;
    @Value("${app.zone}") private String zone;
    @org.springframework.beans.factory.annotation.Autowired private com.tulas.canteen.repo.OrderRepository orders;

    public DataSeeder(UserRepository users, MenuItemRepository menu, AnnouncementRepository announcements,
                      AuthService auth, WalletService wallet, SettingsService settings,
                      com.tulas.canteen.service.MenuService menuService) {
        this.menuService = menuService;
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
            String password = adminPassword;
            if (password == null || password.isBlank()) {
                password = randomPassword();
                log.warn("""

                        ==============================================================
                         ADMIN_PASSWORD was not set, so a random one was generated.
                         Admin login:  {}
                         Password:     {}
                         Log in and change it under Account. This is shown only once.
                        ==============================================================""", adminEmail, password);
            }
            auth.createUser(adminName, adminEmail, password, null, Role.ADMIN);
            log.info("Created admin account {}", adminEmail);
        }
        if (!seedDemo || menu.count() > 0) return;

        var main = canteens.defaultCanteen();
        auth.createUser("Kitchen Counter", "kitchen@tulas.local", "kitchen123", null, Role.STAFF).setCanteenId(main.getId());
        User student = auth.createUser("Demo Student", "student@tulas.local", "student123", "9876543210", Role.STUDENT);
        student.setRollNo("TU-CSE-001");
        wallet.recharge(student.getId(), new BigDecimal("500.00"), "Welcome balance", "system");

        DefaultMenu.DISHES.forEach(d -> menuService.addDefaultDish(d, main.getId()));

        // A second outlet with its own short menu and kitchen login, to show multi-canteen.
        var court = canteens.create(new com.tulas.canteen.dto.AdminDtos.CanteenRequest("Food Court", "F",
                "Near the library", java.time.LocalTime.of(10, 0), java.time.LocalTime.of(20, 0), 3, 6, 45, true, true));
        auth.createUser("Food Court Counter", "foodcourt@tulas.local", "kitchen123", null, Role.STAFF).setCanteenId(court.id());
        for (String name : List.of("Veg Momos (6 pcs)", "Veg Burger", "French Fries", "Paneer Roll", "Masala Maggi",
                "Cold Coffee", "Mango Lassi", "Fresh Lime Soda", "Ice Cream Cup")) {
            DefaultMenu.DISHES.stream().filter(d -> d.name().equals(name)).findFirst()
                    .ifPresent(d -> menuService.addDefaultDish(d, court.id()));
        }
        if (demoHistory) DemoHistory.generate(orders, menu, auth, java.time.ZoneId.of(zone), main);

        Announcement a = new Announcement();
        a.setMessage("Welcome to Tula's Canteen! Order ahead, skip the queue, and collect with your pickup code.");
        announcements.save(a);

        log.info("Demo data added: student@tulas.local / student123, kitchen@tulas.local / kitchen123, foodcourt@tulas.local / kitchen123");
    }

    private static String randomPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 14; i++) sb.append(chars.charAt(r.nextInt(chars.length())));
        return sb.toString();
    }
}
