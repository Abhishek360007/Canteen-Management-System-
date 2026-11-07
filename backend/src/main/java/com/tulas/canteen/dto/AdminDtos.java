package com.tulas.canteen.dto;

import com.tulas.canteen.domain.Announcement;
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
                              int maxQtyPerItem, int noShowMinutes, boolean cashAllowed, boolean openNow) {
        public static CanteenView of(CanteenSettings s, boolean openNow) {
            return new CanteenView(s.getStatus(), s.getOpenTime(), s.getCloseTime(), s.getMaxActiveOrders(),
                    s.getMaxQtyPerItem(), s.getNoShowMinutes(), s.isCashAllowed(), openNow);
        }
    }

    public record StatusRequest(@NotNull CanteenStatus status) {}

    public record SettingsRequest(@NotNull LocalTime openTime, @NotNull LocalTime closeTime,
                                  @Min(1) @Max(10) int maxActiveOrders, @Min(1) @Max(20) int maxQtyPerItem,
                                  @Min(10) @Max(240) int noShowMinutes, boolean cashAllowed) {}

    public record NamedCount(String name, long count, BigDecimal revenue) {}

    public record DayPoint(LocalDate day, long orders, BigDecimal revenue) {}

    public record HourPoint(int hour, long orders) {}

    public record Dashboard(long ordersToday, BigDecimal revenueToday, long activeNow, long studentCount,
                            BigDecimal walletFloat, double avgRating, long noShowsToday, long cancelledToday,
                            List<DayPoint> last7Days, List<NamedCount> topItems, List<HourPoint> busyHours) {}

    public record DaySummary(LocalDate day, long orders, long collected, long cancelled, long noShows, long pending,
                             BigDecimal walletSales, BigDecimal cashSales, BigDecimal cashToCollect,
                             List<NamedCount> items) {}

    public record SpendingView(BigDecimal thisMonth, BigDecimal lastMonth, long ordersThisMonth,
                               List<NamedCount> favourites) {}
}
