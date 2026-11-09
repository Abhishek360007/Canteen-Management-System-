package com.tulas.canteen.dto;

import com.tulas.canteen.domain.Complaint;
import com.tulas.canteen.domain.ComplaintMessage;
import com.tulas.canteen.domain.DishReview;
import com.tulas.canteen.domain.PaymentMethod;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Complaints about orders, and dish reviews. */
public final class FeedbackDtos {

    private FeedbackDtos() {}

    // ---- complaints

    public record RaiseComplaint(@NotNull Complaint.Category category,
                                 @Size(max = 15) List<Long> menuItemIds,
                                 @NotBlank @Size(min = 10, max = 600, message = "please describe the problem in 10-600 characters") String details) {}

    public record MessageBody(@NotBlank @Size(max = 600) String text) {}

    public record ResolveBody(@DecimalMin("0.00") @DecimalMax("5000.00") @Digits(integer = 4, fraction = 2) BigDecimal refund,
                              @Size(max = 300) String note) {}

    public record RejectComplaint(@NotBlank @Size(max = 300) String note) {}

    public record MessageView(Long id, String authorName, boolean fromStaff, String text, Instant createdAt) {
        public static MessageView of(ComplaintMessage m) {
            return new MessageView(m.getId(), m.getAuthorName(), m.isFromStaff(), m.getBody(), m.getCreatedAt());
        }
    }

    /**
     * One complaint. {@code messages}, {@code items} are only filled in on the detail view.
     * {@code maxRefund} is what the student actually paid for the order (a cash no-show paid nothing).
     */
    public record ComplaintView(Long id, Long orderId, String orderCode, LocalDate orderDate, Long canteenId, String canteenName,
                                Complaint.Category category, Complaint.Status status, String details, String dishes,
                                BigDecimal refundAmount, String resolution, String resolvedBy,
                                Instant createdAt, Instant updatedAt, Instant resolvedAt, boolean lastFromStaff,
                                Long studentId, String studentName, String studentEmail, String studentRollNo,
                                BigDecimal orderTotal, PaymentMethod paymentMethod, String orderStatus, BigDecimal maxRefund,
                                List<OrderDtos.LineView> items, List<MessageView> messages) {}

    public record CategoryCount(Complaint.Category category, long count) {}

    public record NameCount(String name, long count) {}

    public record ComplaintStats(long total30d, long open, long resolved30d, long rejected30d, BigDecimal refunded30d,
                                 Double avgHoursToClose, List<CategoryCount> byCategory, List<NameCount> topDishes,
                                 long flaggedReviews) {}

    // ---- dish reviews

    public record ReviewBody(@NotNull Long menuItemId, @Min(1) @Max(5) int stars, @Size(max = 400) String comment) {}

    public record ModerateBody(@NotNull DishReview.Status status, @Size(max = 120) String reason) {}

    /** A review as its author or the public sees it. {@code status} and {@code flagReason} are only for the author. */
    public record ReviewView(Long id, Long menuItemId, String dishName, String author, int stars, String comment,
                             DishReview.Status status, Instant createdAt, boolean mine) {}

    public record DishReviews(Long menuItemId, String name, Double rating, int ratingCount, List<ReviewView> reviews) {}

    public record AdminReviewView(Long id, Long menuItemId, String dishName, Long orderId, int stars, String comment,
                                  DishReview.Status status, String flagReason, String moderatedBy, Instant createdAt,
                                  Long userId, String userName, String userEmail) {}
}
