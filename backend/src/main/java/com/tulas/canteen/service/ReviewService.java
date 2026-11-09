package com.tulas.canteen.service;

import com.tulas.canteen.config.ApiException;
import com.tulas.canteen.domain.DishReview;
import com.tulas.canteen.domain.DishReview.Status;
import com.tulas.canteen.domain.MenuItem;
import com.tulas.canteen.domain.Order;
import com.tulas.canteen.domain.OrderItem;
import com.tulas.canteen.domain.OrderStatus;
import com.tulas.canteen.domain.User;
import com.tulas.canteen.dto.FeedbackDtos.AdminReviewView;
import com.tulas.canteen.dto.FeedbackDtos.DishReviews;
import com.tulas.canteen.dto.FeedbackDtos.ReviewView;
import com.tulas.canteen.repo.DishReviewRepository;
import com.tulas.canteen.repo.MenuItemRepository;
import com.tulas.canteen.repo.OrderRepository;
import com.tulas.canteen.repo.UserRepository;
import com.tulas.canteen.security.AuthUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Dish reviews: stars and optional words for each dish of a collected order.
 * <p>
 * Each (order, dish) counts once towards the dish rating. Rating the whole order gives every dish in it the
 * same stars; a dish review then replaces that dish's share with its own stars instead of adding a second vote.
 * <p>
 * Words that look like a link, phone number, email or abuse are held for an admin to check before anyone sees them.
 */
@Service
public class ReviewService {

    static final int REVIEW_DAYS = 14;

    private static final Pattern LINK = Pattern.compile("(?i)(https?://|www\\.|\\b[a-z0-9-]+\\.(com|in|net|org|io|me|ly|co)\\b)");
    private static final Pattern PHONE = Pattern.compile("(\\+?\\d[\\d\\s-]{8,}\\d)");
    private static final Pattern EMAIL = Pattern.compile("(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}");
    /** A short list of strong words (English and common Hindi transliterations); mild words are fine. */
    private static final Pattern ABUSE = Pattern.compile("(?i)\\b(f+u+c+k\\w*|sh[i1]t\\w*|b[i1]tch\\w*|bastard\\w*|ass ?hole\\w*|"
            + "chut[iy]+a\\w*|madar ?chod\\w*|b[he]+n ?chod\\w*|bc|mc|gaa?ndu\\w*|randi\\w*|haramkhor\\w*|bhosd\\w*|lund\\w*|lauda\\w*)\\b");

    private final DishReviewRepository repo;
    private final OrderRepository orders;
    private final MenuItemRepository menu;
    private final UserRepository users;
    private final AuditService audit;
    private final jakarta.persistence.EntityManager em;

    public ReviewService(DishReviewRepository repo, OrderRepository orders, MenuItemRepository menu,
                         UserRepository users, AuditService audit, jakarta.persistence.EntityManager em) {
        this.em = em;
        this.repo = repo;
        this.orders = orders;
        this.menu = menu;
        this.users = users;
        this.audit = audit;
    }

    // ---------------------------------------------------------------- student

    /** Add or change the review of one dish of a collected order. */
    @Transactional
    public ReviewView review(AuthUser me, Long orderId, Long menuItemId, int stars, String rawComment) {
        Order o = orders.findFull(orderId).orElseThrow(() -> ApiException.notFound("Order not found"));
        if (!o.getUser().getId().equals(me.id())) throw ApiException.notFound("Order not found");
        if (o.getStatus() != OrderStatus.COLLECTED) throw ApiException.badRequest("You can review dishes after collecting the order.");
        Instant collected = o.getClosedAt() == null ? o.getCreatedAt() : o.getClosedAt();
        if (collected.isBefore(Instant.now().minus(Duration.ofDays(REVIEW_DAYS)))) {
            throw ApiException.badRequest("Reviews can be written up to " + REVIEW_DAYS + " days after collecting.");
        }
        // Bumps the order's version: an order rating saved at the same moment fails instead of double-counting.
        em.lock(o, jakarta.persistence.LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        OrderItem line = o.getItems().stream().filter(i -> i.getMenuItemId().equals(menuItemId)).findFirst()
                .orElseThrow(() -> ApiException.badRequest("That dish isn't part of this order."));
        String comment = clean(rawComment);

        DishReview r = repo.findByOrderIdAndMenuItemId(orderId, menuItemId).orElse(null);
        if (r == null) {
            r = new DishReview();
            r.setOrderId(orderId);
            r.setMenuItemId(menuItemId);
            r.setDishName(line.getName());
            r.setUserId(me.id());
            r.setAuthorLabel(authorLabel(o.getUser()));
            // The order's stars (if any) already count for this dish: replace them rather than add a second vote.
            if (o.getRating() != null) menu.addRating(menuItemId, stars - o.getRating(), 0);
            else menu.addRating(menuItemId, stars, 1);
            r.setStars(stars);
            moderate(r, comment, false);
            try {
                repo.saveAndFlush(r);
            } catch (DataIntegrityViolationException e) {
                throw ApiException.conflict("You've already reviewed this dish. Refresh to edit it.");
            }
        } else {
            menu.addRating(menuItemId, stars - r.getStars(), 0);
            boolean wasHidden = r.getStatus() == Status.HIDDEN && r.getModeratedBy() != null;
            boolean textChanged = !Objects.equals(r.getComment(), comment);
            r.setStars(stars);
            if (textChanged) moderate(r, comment, wasHidden);
            r.setUpdatedAt(Instant.now());
        }
        return mine(r);
    }

    @Transactional(readOnly = true)
    public List<ReviewView> forOrder(AuthUser me, Long orderId) {
        Order o = orders.findById(orderId).orElseThrow(() -> ApiException.notFound("Order not found"));
        if (!o.getUser().getId().equals(me.id())) throw ApiException.notFound("Order not found");
        return repo.findByOrderId(orderId).stream().map(this::mine).toList();
    }

    /** What anyone sees for a dish: its rating and the latest visible reviews with words. */
    @Transactional(readOnly = true)
    public DishReviews forDish(AuthUser me, Long menuItemId) {
        MenuItem m = menu.findById(menuItemId).orElseThrow(() -> ApiException.notFound("Dish not found"));
        List<ReviewView> list = repo.findByMenuItemIdAndStatusAndCommentIsNotNullOrderByCreatedAtDesc(
                        menuItemId, Status.VISIBLE, PageRequest.of(0, 30)).stream()
                .map(r -> new ReviewView(r.getId(), r.getMenuItemId(), r.getDishName(), r.getAuthorLabel(), r.getStars(),
                        r.getComment(), null, r.getCreatedAt(), r.getUserId().equals(me.id())))
                .toList();
        return new DishReviews(m.getId(), m.getName(), m.getAverageRating(), m.getRatingCount(), list);
    }

    // ---------------------------------------------------------------- admin

    /** FLAGGED = the moderation queue (oldest first); null = every review with words (newest first). */
    @Transactional(readOnly = true)
    public List<AdminReviewView> list(Status status) {
        List<DishReview> list = status == null ? repo.findTop100ByCommentIsNotNullOrderByCreatedAtDesc()
                : repo.findTop100ByStatusOrderByCreatedAtAsc(status);
        Map<Long, User> people = users.findAllById(list.stream().map(DishReview::getUserId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return list.stream().map(r -> {
            User u = people.get(r.getUserId());
            return new AdminReviewView(r.getId(), r.getMenuItemId(), r.getDishName(), r.getOrderId(), r.getStars(), r.getComment(),
                    r.getStatus(), r.getFlagReason(), r.getModeratedBy(), r.getCreatedAt(), r.getUserId(),
                    u == null ? null : u.getName(), u == null ? null : u.getEmail());
        }).toList();
    }

    /** Show or hide a review's words. Its stars keep counting either way. */
    @Transactional
    public void moderate(AuthUser admin, Long id, Status status, String reason) {
        if (status == Status.FLAGGED) throw ApiException.badRequest("Choose show or hide.");
        DishReview r = repo.findById(id).orElseThrow(() -> ApiException.notFound("Review not found"));
        r.setStatus(status);
        r.setModeratedBy(admin.name());
        r.setFlagReason(status == Status.HIDDEN ? (reason == null || reason.isBlank() ? "Hidden by the canteen" : reason.trim()) : null);
        audit.record(admin, status == Status.HIDDEN ? "REVIEW_HIDDEN" : "REVIEW_SHOWN", r.getDishName(),
                r.getStars() + "★ · \"" + abbreviate(r.getComment(), 80) + "\"" + (status == Status.HIDDEN ? " · " + r.getFlagReason() : ""));
    }

    public long flaggedCount() {
        return repo.countByStatus(Status.FLAGGED);
    }

    // ---------------------------------------------------------------- helpers

    /** Dishes of this order the student already reviewed (their stars replace the order's stars). */
    public Set<Long> reviewedDishes(Long orderId) {
        return repo.findByOrderId(orderId).stream().map(DishReview::getMenuItemId).collect(Collectors.toSet());
    }

    private void moderate(DishReview r, String comment, boolean wasHidden) {
        r.setComment(comment);
        String why = comment == null ? null : flagReason(comment);
        if (why == null && wasHidden) why = "Edited after the canteen hid it";
        r.setStatus(why == null ? Status.VISIBLE : Status.FLAGGED);
        r.setFlagReason(why);
        r.setModeratedBy(null);
    }

    static String flagReason(String text) {
        if (ABUSE.matcher(text).find()) return "Possible abusive language";
        if (LINK.matcher(text).find()) return "Contains a link";
        if (EMAIL.matcher(text).find()) return "Contains an email address";
        if (PHONE.matcher(text).find()) return "Contains a phone number";
        return null;
    }

    private static String clean(String s) {
        if (s == null) return null;
        String t = s.replaceAll("\\s+", " ").trim();
        return t.isEmpty() ? null : t;
    }

    /** "Asha Rao" → "Asha R."; "Asha" → "Asha". */
    static String authorLabel(User u) {
        String[] parts = u.getName().trim().split("\\s+");
        String label = parts.length == 1 ? parts[0] : parts[0] + " " + Character.toUpperCase(parts[parts.length - 1].charAt(0)) + ".";
        return label.length() > 40 ? label.substring(0, 40) : label;
    }

    private static String abbreviate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private ReviewView mine(DishReview r) {
        return new ReviewView(r.getId(), r.getMenuItemId(), r.getDishName(), r.getAuthorLabel(), r.getStars(),
                r.getComment(), r.getStatus(), r.getCreatedAt(), true);
    }
}
