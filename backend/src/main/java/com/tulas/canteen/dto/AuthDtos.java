package com.tulas.canteen.dto;

import com.tulas.canteen.domain.Role;
import com.tulas.canteen.domain.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public final class AuthDtos {

    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Email @Size(max = 120) String email,
            @NotBlank @Size(min = 6, max = 72) String password,
            @Pattern(regexp = "^$|^[0-9]{10}$", message = "must be 10 digits") String phone,
            @Size(max = 30) String rollNo,
            /** Optional friend's invite code. */
            @Size(max = 12) String referralCode) {}

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    public record ChangePasswordRequest(@NotBlank String currentPassword,
                                        @NotBlank @Size(min = 6, max = 72) String newPassword) {}

    public record UpdateProfileRequest(@NotBlank @Size(max = 80) String name,
                                       @Pattern(regexp = "^$|^[0-9]{10}$", message = "must be 10 digits") String phone) {}

    public record UserView(Long id, String name, String email, Role role, String phone, String rollNo,
                           BigDecimal walletBalance, boolean blocked, Instant createdAt, Long canteenId) {
        public static UserView of(User u) {
            return new UserView(u.getId(), u.getName(), u.getEmail(), u.getRole(), u.getPhone(), u.getRollNo(),
                    u.getWalletBalance(), u.isBlocked(), u.getCreatedAt(), u.getCanteenId());
        }
    }

    public record AuthResponse(String token, UserView user) {}
}
