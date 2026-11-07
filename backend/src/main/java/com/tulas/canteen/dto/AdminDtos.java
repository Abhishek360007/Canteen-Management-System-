package com.tulas.canteen.dto;

import com.tulas.canteen.domain.Announcement;
import com.tulas.canteen.domain.AuditLog;
import com.tulas.canteen.domain.TopupRequest;
import com.tulas.canteen.domain.CanteenSettings;
import com.tulas.canteen.domain.CanteenStatus;
import com.tulas.canteen.domain.TxnType;
import com.tulas.canteen.domain.WalletTxn;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class AdminDtos {

    private AdminDtos() {}

    public record RechargeRequest(@NotNull @DecimalMin("10.00") @DecimalMax("5000.00") BigDecimal amount,
                                  @Size(max = 120) String note) {}

    public record CreateStaffRequest(@NotBlank @Size(max = 80) String name,
                                     @NotBlank @Email @Size(max = 120) String email,
                                     @NotBlank @Size(min = 6, max = 72) String password,
                                     @Pattern(regexp = "^$|^[0-9]{10}$", message = "must be 10 digits") String phone) {}

    public record BlockRequest(boolean blocked) {}

    public record TxnView(Long id, TxnType type, BigDecimal amount, BigDecimal balanceAfter, String note,
                          String createdBy, Instant createdAt) {
        public static TxnView of(WalletTxn t) {
            return new TxnView(t.getId(), t.getType(), t.getAmount(), t.getBalanceAfter(), t.getNote(),
                    t.getCreatedBy(), t.getCreatedAt());
        }
    }

    public record WalletView(BigDecimal balance, List<TxnView> transactions) {}

    public record AnnouncementRequest(@NotBlank @Size(max = 280) String message, boolean active) {}

    public record AnnouncementView(Long id, String message, boolean active, Instant createdAt) {
        public static AnnouncementView of(Announcement a) {
            return new AnnouncementView(a.getId(), a.getMessage(), a.isActive(), a.getCreatedAt());
        }
    }

    public record CanteenView(CanteenStatus status, LocalTime openTime, LocalTime closeTime, int maxActiveOrders,
                              int maxQtyPerItem, int noShowMinutes, boolean cashAllowed, boolean openNow,
                              String upiId, String upiName) {
        public static CanteenView of(CanteenSettings s, boolean openNow) {
            return new CanteenView(s.getStatus(), s.getOpenTime(), s.getCloseTime(), s.getMaxActiveOrders(),
                    s.getMaxQtyPerItem(), s.getNoShowMinutes(), s.isCashAllowed(), openNow,
                    s.getUpiId(), s.getUpiName());
        }
    }

    public record StatusRequest(@NotNull CanteenStatus status) {}

    public record SettingsRequest(@NotNull LocalTime openTime, @NotNull LocalTime closeTime,
                                  @Min(1) @Max(10) int maxActiveOrders, @Min(1) @Max(20) int maxQtyPerItem,
                                  @Min(10) @Max(240) int noShowMinutes, boolean cashAllowed,
                                  @Size(max = 60) @Pattern(regexp = "^$|^[A-Za-z0-9._-]{2,}@[A-Za-z]{2,}$",
                                          message = "must look like name@bank") String upiId,
                                  @Size(max = 60) String upiName) {}

    // ---- UPI top-ups

    public record TopupBody(@NotNull @DecimalMin("10.00") @DecimalMax("5000.00") @Digits(integer = 4, fraction = 2) BigDecimal amount,
                            @NotBlank @Pattern(regexp = "^[A-Za-z0-9]{6,30}$",
                                    message = "should be the 12-digit UPI reference / UTR from your payment app") String upiRef) {}

    public record RejectBody(@Size(max = 200) String reason) {}

    public record TopupView(Long id, BigDecimal amount, String upiRef, TopupRequest.Status status, String reason,
                            String decidedBy, Instant createdAt, Instant decidedAt,
                            Long studentId, String studentName, String studentEmail, String studentRollNo) {
        public static TopupView of(TopupRequest t) {
            var u = t.getUser();
            return new TopupView(t.getId(), t.getAmount(), t.getUpiRef(), t.getStatus(), t.getReason(),
                    t.getDecidedBy(), t.getCreatedAt(), t.getDecidedAt(),
                    u.getId(), u.getName(), u.getEmail(), u.getRollNo());
        }
    }

    // ---- activity log

    public record AuditView(Long id, String actorName, String action, String target, String details, Instant createdAt) {
        public static AuditView of(AuditLog a) {
            return new AuditView(a.getId(), a.getActorName(), a.getAction(), a.getTarget(), a.getDetails(), a.getCreatedAt());
        }
    }

    public record TempPasswordView(String tempPassword) {}

    public record ImportResult(int added, int skipped) {}

    public record NamedCount(String name, long count, BigDecimal revenue) {}

    public record DayPoint(LocalDate day, long orders, BigDecimal revenue) {}

    public record HourPoint(int hour, long orders) {}

    public record Dashboard(long ordersToday, BigDecimal revenueToday, long activeNow, long studentCount,
                            BigDecimal walletFloat, double avgRating, long noShowsToday, long cancelledToday,
                            long pendingTopups,
                            List<DayPoint> last7Days, List<NamedCount> topItems, List<HourPoint> busyHours) {}

    public record DaySummary(LocalDate day, long orders, long collected, long cancelled, long noShows, long pending,
                             BigDecimal walletSales, BigDecimal cashSales, BigDecimal cashToCollect,
                             List<NamedCount> items) {}

    public record SpendingView(BigDecimal thisMonth, BigDecimal lastMonth, long ordersThisMonth,
                               List<NamedCount> favourites) {}
}
