package com.tulas.canteen.web;

import com.tulas.canteen.domain.Complaint;
import com.tulas.canteen.domain.DishReview;
import com.tulas.canteen.dto.FeedbackDtos.*;
import com.tulas.canteen.security.AuthUser;
import com.tulas.canteen.service.ComplaintService;
import com.tulas.canteen.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Complaints and dish reviews. Access follows the path rules in SecurityConfig:
 * /api/orders/** is for students, /api/admin/** for admins, /api/reviews/** for anyone logged in.
 */
@RestController
@RequestMapping("/api")
public class FeedbackController {

    private final ComplaintService complaints;
    private final ReviewService reviews;

    public FeedbackController(ComplaintService complaints, ReviewService reviews) {
        this.complaints = complaints;
        this.reviews = reviews;
    }

    // ---- student: complaints

    @PostMapping("/orders/{id}/complaint")
    @ResponseStatus(HttpStatus.CREATED)
    public ComplaintView raise(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @Valid @RequestBody RaiseComplaint r) {
        return complaints.raise(me, id, r);
    }

    /** The complaint on this order; an empty body if there is none. */
    @GetMapping("/orders/{id}/complaint")
    public ComplaintView complaintFor(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return complaints.forOrder(me, id);
    }

    @PostMapping("/orders/{id}/complaint/messages")
    public ComplaintView studentReply(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @Valid @RequestBody MessageBody b) {
        return complaints.replyToMine(me, id, b.text());
    }

    @GetMapping("/orders/complaints")
    public List<ComplaintView> myComplaints(@AuthenticationPrincipal AuthUser me) {
        return complaints.mine(me);
    }

    // ---- student: dish reviews

    @PostMapping("/orders/{id}/reviews")
    public ReviewView review(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @Valid @RequestBody ReviewBody b) {
        return reviews.review(me, id, b.menuItemId(), b.stars(), b.comment());
    }

    @GetMapping("/orders/{id}/reviews")
    public List<ReviewView> myReviews(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        return reviews.forOrder(me, id);
    }

    // ---- anyone: what people say about a dish

    @GetMapping("/reviews/dish/{menuItemId}")
    public DishReviews dish(@AuthenticationPrincipal AuthUser me, @PathVariable Long menuItemId) {
        return reviews.forDish(me, menuItemId);
    }

    // ---- admin

    @GetMapping("/admin/complaints")
    public List<ComplaintView> list(@RequestParam(required = false) Complaint.Status status,
                                    @RequestParam(required = false) Long canteenId) {
        return complaints.list(status, canteenId);
    }

    @GetMapping("/admin/complaints/stats")
    public ComplaintStats stats(@RequestParam(required = false) Long canteenId) {
        return complaints.stats(canteenId);
    }

    @GetMapping("/admin/complaints/{id}")
    public ComplaintView one(@PathVariable Long id) {
        return complaints.get(id);
    }

    @PostMapping("/admin/complaints/{id}/messages")
    public ComplaintView adminReply(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @Valid @RequestBody MessageBody b) {
        return complaints.reply(me, id, b.text());
    }

    @PostMapping("/admin/complaints/{id}/resolve")
    public ComplaintView resolve(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @Valid @RequestBody ResolveBody b) {
        return complaints.resolve(me, id, b.refund(), b.note());
    }

    @PostMapping("/admin/complaints/{id}/reject")
    public ComplaintView reject(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @Valid @RequestBody RejectComplaint b) {
        return complaints.reject(me, id, b.note());
    }

    /** status=FLAGGED for the moderation queue; no status for every review with words. */
    @GetMapping("/admin/reviews")
    public List<AdminReviewView> allReviews(@RequestParam(required = false) DishReview.Status status) {
        return reviews.list(status);
    }

    @PostMapping("/admin/reviews/{id}/moderate")
    public void moderate(@AuthenticationPrincipal AuthUser me, @PathVariable Long id, @Valid @RequestBody ModerateBody b) {
        reviews.moderate(me, id, b.status(), b.reason());
    }
}
