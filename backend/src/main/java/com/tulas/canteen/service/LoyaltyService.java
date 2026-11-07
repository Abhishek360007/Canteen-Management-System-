package com.tulas.canteen.service;

import com.tulas.canteen.config.ApiException;
import com.tulas.canteen.domain.LoyaltyTxn;
import com.tulas.canteen.domain.LoyaltyTxn.Type;
import com.tulas.canteen.domain.Order;
import com.tulas.canteen.domain.Role;
import com.tulas.canteen.domain.User;
import com.tulas.canteen.repo.LoyaltyTxnRepository;
import com.tulas.canteen.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.List;
import java.util.Map;

/**
 * Reward points.
 * <ul>
 *   <li>Earn 1 point per ₹10 paid, when the order is collected (not when placed, so cancelled orders earn nothing).
 *       Silver and Gold tiers earn 1.25× and 1.5×.</li>
 *   <li>Redeem in steps of 10 points; 10 points = ₹5 off, up to half the order.</li>
 *   <li>Birthday: 50 points once a year. Referral: 50 points each when the invited friend's first order is collected.</li>
 * </ul>
 * Points move only through atomic UPDATEs and every change writes a ledger row, like the wallet.
 */
@Service
public class LoyaltyService {

    private static final Logger log = LoggerFactory.getLogger(LoyaltyService.class);

    public static final int REDEEM_STEP = 10;
    /** Rupee value of one point when redeemed. */
    public static final BigDecimal POINT_VALUE = new BigDecimal("0.50");
    public static final int BIRTHDAY_BONUS = 50;
    public static final int REFERRAL_BONUS = 50;

    public enum Tier {
        BRONZE(BigDecimal.ZERO, 1.0), SILVER(new BigDecimal("2000"), 1.25), GOLD(new BigDecimal("5000"), 1.5);

        public final BigDecimal minSpend;
        public final double multiplier;

        Tier(BigDecimal minSpend, double multiplier) {
            this.minSpend = minSpend;
            this.multiplier = multiplier;
        }

        public static Tier of(BigDecimal lifetimeSpend) {
            Tier t = BRONZE;
            for (Tier x : values()) if (lifetimeSpend.compareTo(x.minSpend) >= 0) t = x;
            return t;
        }

        public Tier next() {
            return this == GOLD ? null : values()[ordinal() + 1];
        }
    }

    private final UserRepository users;
    private final LoyaltyTxnRepository txns;
    private final LiveHub live;
    private final Clock clock;
    private final NotificationService notifications;
    private final SecureRandom random = new SecureRandom();

    public LoyaltyService(UserRepository users, LoyaltyTxnRepository txns, LiveHub live, Clock clock,
                          NotificationService notifications) {
        this.notifications = notifications;
        this.users = users;
        this.txns = txns;
        this.live = live;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- checkout

    /** Rupee discount for spending these points on this subtotal, after checking the rules. */
    public BigDecimal discountFor(int points, BigDecimal subtotal) {
        if (points % REDEEM_STEP != 0) throw ApiException.badRequest("Use points in steps of " + REDEEM_STEP + ".");
        BigDecimal discount = POINT_VALUE.multiply(BigDecimal.valueOf(points));
        BigDecimal max = subtotal.divide(BigDecimal.valueOf(2), 2, RoundingMode.DOWN);
        if (discount.compareTo(max) > 0) {
            throw ApiException.badRequest("Points can cover at most half the order (₹" + max.toPlainString() + ").");
        }
        return discount;
    }

    /** Takes the points at checkout; throws (rolling back the whole order) if the balance is too low. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void spend(Long userId, int points, String orderCode) {
        if (users.spendPoints(userId, points) == 0) {
            throw ApiException.badRequest("You have only " + users.pointsOf(userId) + " points.");
        }
        record(userId, Type.REDEEM, -points, "Used on order " + orderCode);
    }

    /** Cancelled order: the points used on it come back. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void restore(Order o) {
        int p = o.getPointsRedeemed();
        if (p <= 0) return;
        users.addPoints(o.getUser().getId(), p);
        record(o.getUser().getId(), Type.RESTORE, p, "Returned from cancelled order " + o.getCode());
    }

    // ---------------------------------------------------------------- earning

    /** Order collected: earn points on what was paid, add to lifetime spend, and settle a pending referral. */
    @Transactional(propagation = Propagation.MANDATORY)
    public int earn(Order o) {
        User u = users.findById(o.getUser().getId()).orElseThrow();
        if (u.getRole() != Role.STUDENT) return 0;
        Tier tier = Tier.of(u.getLifetimeSpend());
        int points = (int) Math.floor(o.getTotal().doubleValue() / 10.0 * tier.multiplier);
        users.addLifetimeSpend(u.getId(), o.getTotal());
        if (points > 0) {
            users.addPoints(u.getId(), points);
            record(u.getId(), Type.EARN, points, "Order " + o.getCode()
                    + (tier == Tier.BRONZE ? "" : " · " + tier.name().charAt(0) + tier.name().substring(1).toLowerCase() + " " + tier.multiplier + "×"));
        }
        o.setPointsEarned(points);

        if (u.getReferredById() != null && !u.isReferralRewarded()) {
            u.setReferralRewarded(true);
            users.addPoints(u.getId(), REFERRAL_BONUS);
            record(u.getId(), Type.REFERRAL, REFERRAL_BONUS, "Welcome bonus for joining with a friend's code");
            users.findById(u.getReferredById()).filter(r -> r.getRole() == Role.STUDENT).ifPresent(r -> {
                users.addPoints(r.getId(), REFERRAL_BONUS);
                record(r.getId(), Type.REFERRAL, REFERRAL_BONUS, u.getName() + " joined with your code");
            });
        }
        return points;
    }

    /** Every morning: 50 points to students whose birthday is today (29 Feb birthdays get it on 28 Feb in other years). */
    @Scheduled(cron = "0 0 7 * * *", zone = "${app.zone}")
    @Transactional
    public int birthdayBonuses() {
        LocalDate today = clock.today();
        int given = 0;
        for (User u : users.findAllWithBirthday()) {
            MonthDay bday = MonthDay.from(u.getBirthday());
            if (bday.equals(MonthDay.of(2, 29)) && !today.isLeapYear()) bday = MonthDay.of(2, 28);
            if (!bday.equals(MonthDay.from(today)) || Integer.valueOf(today.getYear()).equals(u.getBirthdayBonusYear())) continue;
            u.setBirthdayBonusYear(today.getYear());
            users.addPoints(u.getId(), BIRTHDAY_BONUS);
            record(u.getId(), Type.BIRTHDAY, BIRTHDAY_BONUS, "Happy birthday from Tula's Canteen! 🎂");
            notifications.notify(u.getId(), "POINTS", "Happy birthday! 🎂",
                    "Here are " + BIRTHDAY_BONUS + " bonus points from Tula's Canteen.", "/rewards");
            given++;
        }
        if (given > 0) log.info("Gave birthday bonus to {} student(s)", given);
        return given;
    }

    // ---------------------------------------------------------------- referral codes & summary

    /** A short, readable, unique code like "ASHA7K2". */
    @Transactional
    public String ensureReferralCode(User u) {
        if (u.getReferralCode() != null) return u.getReferralCode();
        String base = u.getName().replaceAll("[^A-Za-z]", "").toUpperCase();
        base = (base + "TULA").substring(0, 4);
        String alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
        String code;
        do {
            StringBuilder sb = new StringBuilder(base);
            for (int i = 0; i < 3; i++) sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
            code = sb.toString();
        } while (users.existsByReferralCodeIgnoreCase(code));
        u.setReferralCode(code);
        return code;
    }

    public record TxnView(Long id, Type type, int points, int balanceAfter, String note, java.time.Instant createdAt) {
        static TxnView of(LoyaltyTxn t) {
            return new TxnView(t.getId(), t.getType(), t.getPoints(), t.getBalanceAfter(), t.getNote(), t.getCreatedAt());
        }
    }

    public record Summary(int points, BigDecimal pointsValue, Tier tier, double multiplier, Tier nextTier,
                          BigDecimal spendToNextTier, BigDecimal lifetimeSpend, String referralCode,
                          LocalDate birthday, int redeemStep, BigDecimal pointValue, List<TxnView> history) {}

    @Transactional
    public Summary summary(Long userId) {
        User u = users.findById(userId).orElseThrow();
        String code = ensureReferralCode(u);
        Tier tier = Tier.of(u.getLifetimeSpend());
        Tier next = tier.next();
        int points = u.getLoyaltyPoints();
        return new Summary(points, POINT_VALUE.multiply(BigDecimal.valueOf(points)), tier, tier.multiplier, next,
                next == null ? null : next.minSpend.subtract(u.getLifetimeSpend()).max(BigDecimal.ZERO),
                u.getLifetimeSpend(), code, u.getBirthday(), REDEEM_STEP, POINT_VALUE,
                txns.findTop50ByUserIdOrderByCreatedAtDesc(userId).stream().map(TxnView::of).toList());
    }

    /** Birthday can be set once; afterwards only an admin could change it. */
    @Transactional
    public void setBirthday(Long userId, LocalDate birthday) {
        User u = users.findById(userId).orElseThrow();
        if (u.getBirthday() != null) throw ApiException.badRequest("Your birthday is already saved.");
        LocalDate today = clock.today();
        if (birthday.isAfter(today.minusYears(10)) || birthday.isBefore(today.minusYears(100))) {
            throw ApiException.badRequest("Please enter your real date of birth.");
        }
        u.setBirthday(birthday);
        // Setting it on the day itself doesn't give a same-day bonus (stops last-minute gaming).
        u.setBirthdayBonusYear(today.getYear());
    }

    private void record(Long userId, Type type, int points, String note) {
        int after = users.pointsOf(userId);
        txns.save(new LoyaltyTxn(users.getReferenceById(userId), type, points, after, note));
        live.toUser(userId, "points", Map.of("points", after));
    }
}
