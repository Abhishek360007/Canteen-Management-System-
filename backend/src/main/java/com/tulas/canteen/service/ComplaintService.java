package com.tulas.canteen.service;

import com.tulas.canteen.config.ApiException;
import com.tulas.canteen.domain.*;
import com.tulas.canteen.domain.Complaint.Category;
import com.tulas.canteen.domain.Complaint.Status;
import com.tulas.canteen.dto.FeedbackDtos.*;
import com.tulas.canteen.dto.OrderDtos.LineView;
import com.tulas.canteen.repo.ComplaintMessageRepository;
import com.tulas.canteen.repo.ComplaintRepository;
import com.tulas.canteen.repo.OrderRepository;
import com.tulas.canteen.repo.UserRepository;
import com.tulas.canteen.security.AuthUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Complaints about orders. A student reports a problem with a collected (or not-collected) order within
 * {@value #WINDOW_HOURS} hours; admins talk it through in a thread and close it as resolved, optionally refunding up
 * to what the student paid into their wallet, or rejected with a reason. The {@code @Version} on the complaint
 * means two admins can't both resolve (and refund) the same one.
 */
@Service
public class ComplaintService {

    static final int WINDOW_HOURS = 48;
    static final int MAX_OPEN_PER_STUDENT = 3;
    static final int MAX_MESSAGES = 30;
    private static final List<OrderStatus> REPORTABLE = List.of(OrderStatus.COLLECTED, OrderStatus.NO_SHOW);

    private final ComplaintRepository repo;
    private final ComplaintMessageRepository messages;
    private final OrderRepository orders;
    private final UserRepository users;
    private final WalletService wallet;
    private final NotificationService notifications;
    private final AuditService audit;
    private final LiveHub live;
    private final ReviewService reviews;

    public ComplaintService(ComplaintRepository repo, ComplaintMessageRepository messages, OrderRepository orders,
                            UserRepository users, WalletService wallet, NotificationService notifications,
                            AuditService audit, LiveHub live, ReviewService reviews) {
        this.repo = repo;
        this.messages = messages;
        this.orders = orders;
        this.users = users;
        this.wallet = wallet;
        this.notifications = notifications;
        this.audit = audit;
        this.live = live;
        this.reviews = reviews;
    }

    // ---------------------------------------------------------------- student

    @Transactional
    public ComplaintView raise(AuthUser me, Long orderId, RaiseComplaint r) {
        Order o = orders.findFull(orderId).orElseThrow(() -> ApiException.notFound("Order not found"));
        if (!o.getUser().getId().equals(me.id())) throw ApiException.notFound("Order not found");
        if (!REPORTABLE.contains(o.getStatus())) {
            throw ApiException.badRequest(o.getStatus().isActive()
                    ? "Your order is still being made. Please talk to the counter staff if something is wrong."
                    : "This order was cancelled" + (o.getPaymentMethod() == PaymentMethod.WALLET ? " and refunded." : "."));
        }
        Instant closed = o.getClosedAt() == null ? o.getCreatedAt() : o.getClosedAt();
        if (closed.isBefore(Instant.now().minus(Duration.ofHours(WINDOW_HOURS)))) {
            throw ApiException.badRequest("Problems can be reported up to " + WINDOW_HOURS
                    + " hours after the order. Please visit the canteen office.");
        }
        if (repo.existsByOrderId(orderId)) throw ApiException.conflict("You've already reported a problem with this order.");
        if (repo.countByUserIdAndStatus(me.id(), Status.OPEN) >= MAX_OPEN_PER_STUDENT) {
            throw ApiException.badRequest("You already have " + MAX_OPEN_PER_STUDENT
                    + " open complaints. Please wait for the canteen to answer those first.");
        }
        Set<Long> picked = r.menuItemIds() == null ? Set.of() : new HashSet<>(r.menuItemIds());
        String dishes = o.getItems().stream().filter(i -> picked.contains(i.getMenuItemId()))
                .map(OrderItem::getName).distinct().collect(Collectors.joining(", "));

        Complaint c = new Complaint();
        c.setOrder(o);
        c.setUser(o.getUser());
        c.setCanteenId(o.getCanteen() == null ? null : o.getCanteen().getId());
        c.setCategory(r.category());
        c.setDetails(r.details().trim());
        c.setDishes(dishes.isEmpty() ? null : truncate(dishes, 300));
        try {
            repo.saveAndFlush(c);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("You've already reported a problem with this order.");
        }

        notifyAdmins("New complaint · order " + o.getCode(),
                label(r.category()) + (dishes.isEmpty() ? "" : " (" + dishes + ")") + ": " + truncate(c.getDetails(), 120),
                c);
        live.toStaff("complaint", Map.of("id", c.getId()));
        return view(c, true);
    }

    /** The complaint on one of my orders, or null if there isn't one. */
    @Transactional(readOnly = true)
    public ComplaintView forOrder(AuthUser me, Long orderId) {
        return repo.findByOrderId(orderId).filter(c -> c.getUser().getId().equals(me.id()))
                .map(c -> view(c, true)).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<ComplaintView> mine(AuthUser me) {
        return repo.findTop50ByUserIdOrderByCreatedAtDesc(me.id()).stream().map(c -> view(c, false)).toList();
    }

    /** A message in the thread, from the student who raised it or from an admin. */
    @Transactional
    public ComplaintView reply(AuthUser me, Long complaintId, String text) {
        Complaint c = repo.findWithOrderById(complaintId).orElseThrow(() -> ApiException.notFound("Complaint not found"));
        boolean staff = me.role() == Role.ADMIN;
        if (!staff && !c.getUser().getId().equals(me.id())) throw ApiException.notFound("Complaint not found");
        if (c.getStatus() != Status.OPEN) throw ApiException.badRequest("This complaint is closed.");
        if (messages.countByComplaintId(c.getId()) >= MAX_MESSAGES) {
            throw ApiException.badRequest("This conversation is too long. Please visit the canteen office.");
        }
        messages.save(new ComplaintMessage(c.getId(), me.id(), me.name(), staff, text.trim()));
        c.touch(staff);
        String code = c.getOrder().getCode();
        if (staff) {
            notifications.notify(c.getUser().getId(), "COMPLAINT", "Reply about order " + code,
                    truncate(text.trim(), 160), "/orders/" + c.getOrder().getId());
        } else {
            notifyAdmins("Student replied · order " + code, truncate(text.trim(), 160), c);
        }
        publish(c);
        return view(c, true);
    }

    @Transactional
    public ComplaintView replyToMine(AuthUser me, Long orderId, String text) {
        Complaint c = repo.findByOrderId(orderId).filter(x -> x.getUser().getId().equals(me.id()))
                .orElseThrow(() -> ApiException.notFound("Complaint not found"));
        return reply(me, c.getId(), text);
    }

    // ---------------------------------------------------------------- admin

    @Transactional(readOnly = true)
    public List<ComplaintView> list(Status status, Long canteenId) {
        Collection<Status> statuses = status == null ? EnumSet.allOf(Status.class) : List.of(status);
        return repo.search(statuses, canteenId == null, canteenId == null ? -1 : canteenId, PageRequest.of(0, 100))
                .stream().map(c -> view(c, false)).toList();
    }

    @Transactional(readOnly = true)
    public ComplaintView get(Long id) {
        return view(repo.findWithOrderById(id).orElseThrow(() -> ApiException.notFound("Complaint not found")), true);
    }

    /** Close as resolved, optionally refunding up to what the student paid into their wallet. */
    @Transactional
    public ComplaintView resolve(AuthUser admin, Long id, BigDecimal rawRefund, String rawNote) {
        Complaint c = open(id);
        Order o = c.getOrder();
        BigDecimal refund = rawRefund == null ? BigDecimal.ZERO : rawRefund.setScale(2, RoundingMode.HALF_UP);
        BigDecimal max = paid(o);
        if (refund.compareTo(max) > 0) {
            throw ApiException.badRequest(max.signum() == 0
                    ? "Nothing was paid for this order (cash, not collected), so there's nothing to refund."
                    : "The refund can't be more than the " + AuditService.rupees(max) + " paid for this order.");
        }
        if (c.getUser().getRole() != Role.STUDENT && refund.signum() > 0) throw ApiException.badRequest("Only students have a wallet.");
        String note = rawNote == null || rawNote.isBlank() ? null : rawNote.trim();
        c.close(Status.RESOLVED, admin.name(), note, refund.signum() > 0 ? refund : null);
        repo.saveAndFlush(c); // version check: a second admin resolving at the same time fails here, before any money moves
        if (refund.signum() > 0) wallet.complaintRefund(c.getUser().getId(), refund, o.getCode(), admin.name());

        audit.record(admin, "COMPLAINT_RESOLVED", AuditService.who(c.getUser()),
                "Order " + o.getCode() + " · " + label(c.getCategory())
                        + (refund.signum() > 0 ? " · refunded " + AuditService.rupees(refund) : " · no refund")
                        + (note == null ? "" : " · " + note));
        notifications.notify(c.getUser().getId(), "COMPLAINT",
                refund.signum() > 0 ? AuditService.rupees(refund) + " refunded for order " + o.getCode()
                        : "Your complaint about " + o.getCode() + " was resolved",
                note == null ? "Thanks for telling us. We've looked into it." : truncate(note, 200),
                "/orders/" + o.getId());
        publish(c);
        return view(c, true);
    }

    @Transactional
    public ComplaintView reject(AuthUser admin, Long id, String rawNote) {
        Complaint c = open(id);
        String note = rawNote.trim();
        c.close(Status.REJECTED, admin.name(), note, null);
        repo.saveAndFlush(c);
        audit.record(admin, "COMPLAINT_REJECTED", AuditService.who(c.getUser()),
                "Order " + c.getOrder().getCode() + " · " + label(c.getCategory()) + " · " + note);
        notifications.notify(c.getUser().getId(), "COMPLAINT", "Update on your complaint about " + c.getOrder().getCode(),
                truncate(note, 200), "/orders/" + c.getOrder().getId());
        publish(c);
        return view(c, true);
    }

    public long openCount() {
        return repo.countByStatus(Status.OPEN);
    }

    /** The last 30 days: how many, what about, how fast they're closed, refunds, and the dishes named most. */
    @Transactional(readOnly = true)
    public ComplaintStats stats(Long canteenId) {
        List<Complaint> recent = repo.since(Instant.now().minus(Duration.ofDays(30)), canteenId == null, canteenId == null ? -1 : canteenId);
        Map<Category, Long> byCat = recent.stream().collect(Collectors.groupingBy(Complaint::getCategory, Collectors.counting()));
        List<CategoryCount> cats = byCat.entrySet().stream()
                .sorted(Map.Entry.<Category, Long>comparingByValue().reversed())
                .map(e -> new CategoryCount(e.getKey(), e.getValue())).toList();
        Map<String, Long> dishCounts = recent.stream().filter(c -> c.getDishes() != null)
                .flatMap(c -> Arrays.stream(c.getDishes().split(",\\s*")))
                .collect(Collectors.groupingBy(s -> s, Collectors.counting()));
        List<NameCount> dishes = dishCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(5).map(e -> new NameCount(e.getKey(), e.getValue())).toList();
        OptionalDouble hours = recent.stream().filter(c -> c.getResolvedAt() != null)
                .mapToLong(c -> Duration.between(c.getCreatedAt(), c.getResolvedAt()).toMinutes()).average();
        BigDecimal refunded = recent.stream().map(Complaint::getRefundAmount).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long open = canteenId == null ? repo.countByStatus(Status.OPEN) : repo.countByStatusAndCanteenId(Status.OPEN, canteenId);
        return new ComplaintStats(recent.size(), open,
                recent.stream().filter(c -> c.getStatus() == Status.RESOLVED).count(),
                recent.stream().filter(c -> c.getStatus() == Status.REJECTED).count(),
                refunded,
                hours.isPresent() ? Math.round(hours.getAsDouble() / 6.0) / 10.0 : null,
                cats, dishes, reviews.flaggedCount());
    }

    // ---------------------------------------------------------------- helpers

    /** What the student actually paid: wallet orders and collected cash orders. A cash no-show paid nothing. */
    static BigDecimal paid(Order o) {
        if (o.getPaymentMethod() == PaymentMethod.CASH && o.getStatus() != OrderStatus.COLLECTED) return BigDecimal.ZERO;
        return o.getTotal();
    }

    private Complaint open(Long id) {
        Complaint c = repo.findWithOrderById(id).orElseThrow(() -> ApiException.notFound("Complaint not found"));
        if (c.getStatus() != Status.OPEN) {
            throw ApiException.conflict("This complaint was already " + c.getStatus().name().toLowerCase()
                    + (c.getResolvedBy() == null ? "." : " by " + c.getResolvedBy() + "."));
        }
        return c;
    }

    private void notifyAdmins(String title, String body, Complaint c) {
        users.findIdsByRoleIn(List.of(Role.ADMIN))
                .forEach(id -> notifications.notify(id, "COMPLAINT", title, body, "/admin/complaints/" + c.getId()));
    }

    private void publish(Complaint c) {
        live.toUser(c.getUser().getId(), "complaint", Map.of("id", c.getId(), "orderId", c.getOrder().getId()));
        live.toStaff("complaint", Map.of("id", c.getId()));
    }

    private ComplaintView view(Complaint c, boolean full) {
        Order o = c.getOrder();
        User u = c.getUser();
        List<LineView> items = full ? o.getItems().stream().map(LineView::of).toList() : null;
        List<MessageView> thread = full ? messages.findByComplaintIdOrderByCreatedAtAscIdAsc(c.getId()).stream()
                .map(MessageView::of).toList() : null;
        return new ComplaintView(c.getId(), o.getId(), o.getCode(), o.getOrderDate(),
                o.getCanteen() == null ? null : o.getCanteen().getId(), o.getCanteen() == null ? null : o.getCanteen().getName(),
                c.getCategory(), c.getStatus(), c.getDetails(), c.getDishes(), c.getRefundAmount(), c.getResolution(),
                c.getResolvedBy(), c.getCreatedAt(), c.getUpdatedAt(), c.getResolvedAt(), c.isLastFromStaff(),
                u.getId(), u.getName(), u.getEmail(), u.getRollNo(),
                o.getTotal(), o.getPaymentMethod(), o.getStatus().name(), paid(o), items, thread);
    }

    static String label(Category c) {
        return switch (c) {
            case WRONG_ITEM -> "Wrong item";
            case MISSING_ITEM -> "Missing item";
            case QUALITY -> "Food quality";
            case QUANTITY -> "Small portion";
            case HYGIENE -> "Hygiene";
            case LATE -> "Late / not ready";
            case OVERCHARGED -> "Charged wrongly";
            case STAFF -> "Staff behaviour";
            case OTHER -> "Other";
        };
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
