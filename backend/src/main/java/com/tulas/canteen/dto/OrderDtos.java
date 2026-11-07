package com.tulas.canteen.dto;

import com.tulas.canteen.domain.Order;
import com.tulas.canteen.domain.OrderItem;
import com.tulas.canteen.domain.OrderStatus;
import com.tulas.canteen.domain.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class OrderDtos {

    private OrderDtos() {}

    public record CartLine(@NotNull Long menuItemId, @Min(1) @Max(20) int quantity) {}

    public record PlaceOrderRequest(
            @NotEmpty @Size(max = 15) List<@Valid CartLine> items,
            @NotNull PaymentMethod paymentMethod,
            /** "HH:mm", or empty for as soon as possible. */
            @Pattern(regexp = "^$|^([01]\\d|2[0-3]):[0-5]\\d$", message = "must be HH:mm") String pickupSlot,
            @Size(max = 200) String note,
            /** Reward points to use (steps of 10); null/0 = none. */
            @Min(0) @Max(100000) Integer redeemPoints) {}

    public record StatusUpdate(@NotNull OrderStatus status, @Size(max = 200) String reason) {}

    public record VerifyRequest(@NotBlank @Size(max = 12) String code, @NotBlank @Pattern(regexp = "\\d{4}") String otp) {}

    public record RateRequest(@Min(1) @Max(5) int stars) {}

    public record LineView(Long menuItemId, String name, BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {
        static LineView of(OrderItem i) {
            return new LineView(i.getMenuItemId(), i.getName(), i.getUnitPrice(), i.getQuantity(), i.getLineTotal());
        }
    }

    /**
     * One order. {@code otp} is only filled in for the student who owns the order;
     * {@code ahead}/{@code etaMinutes} only while it is still being made.
     */
    public record OrderView(Long id, String code, LocalDate orderDate, OrderStatus status, PaymentMethod paymentMethod,
                            BigDecimal total, String otp, LocalTime pickupSlot, String note, Integer rating,
                            String cancelReason, Instant createdAt, Instant readyAt, Instant closedAt,
                            String customerName, String customerRollNo, List<LineView> items,
                            Long ahead, Integer etaMinutes,
                            BigDecimal subtotal, BigDecimal discount, int pointsRedeemed, int pointsEarned) {

        public static OrderView of(Order o, boolean showOtp, Long ahead, Integer eta) {
            return new OrderView(o.getId(), o.getCode(), o.getOrderDate(), o.getStatus(), o.getPaymentMethod(),
                    o.getTotal(), showOtp ? o.getOtp() : null, o.getPickupSlot(), o.getNote(), o.getRating(),
                    o.getCancelReason(), o.getCreatedAt(), o.getReadyAt(), o.getClosedAt(),
                    o.getUser().getName(), o.getUser().getRollNo(),
                    o.getItems().stream().map(LineView::of).toList(), ahead, eta,
                    o.getTotal().add(o.getDiscount()), o.getDiscount(), o.getPointsRedeemed(), o.getPointsEarned());
        }

        public static OrderView staff(Order o) {
            return of(o, false, null, null);
        }
    }
}
