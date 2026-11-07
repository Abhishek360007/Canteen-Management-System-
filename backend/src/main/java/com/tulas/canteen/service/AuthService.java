package com.tulas.canteen.service;

import com.tulas.canteen.config.ApiException;
import com.tulas.canteen.domain.Role;
import com.tulas.canteen.domain.User;
import com.tulas.canteen.dto.AuthDtos.*;
import com.tulas.canteen.repo.UserRepository;
import com.tulas.canteen.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    /** Public sign-up always creates a STUDENT. Staff accounts are created by the admin. */
    @Transactional
    public AuthResponse register(RegisterRequest r) {
        User u = createUser(r.name(), r.email(), r.password(), r.phone(), Role.STUDENT);
        u.setRollNo(blankToNull(r.rollNo()));
        return new AuthResponse(jwt.issue(u), UserView.of(u));
    }

    @Transactional
    public User createUser(String name, String email, String password, String phone, Role role) {
        String mail = email.trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(mail)) throw ApiException.conflict("An account with this email already exists.");
        User u = new User();
        u.setName(name.trim());
        u.setEmail(mail);
        u.setPasswordHash(encoder.encode(password));
        u.setPhone(blankToNull(phone));
        u.setRole(role);
        return users.save(u);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest r) {
        User u = users.findByEmailIgnoreCase(r.email().trim())
                .filter(x -> encoder.matches(r.password(), x.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Wrong email or password."));
        if (u.isBlocked()) throw ApiException.forbidden("Your account is blocked. Please contact the canteen office.");
        return new AuthResponse(jwt.issue(u), UserView.of(u));
    }

    @Transactional(readOnly = true)
    public UserView me(Long id) {
        return UserView.of(load(id));
    }

    @Transactional
    public UserView updateProfile(Long id, UpdateProfileRequest r) {
        User u = load(id);
        u.setName(r.name().trim());
        u.setPhone(blankToNull(r.phone()));
        return UserView.of(u);
    }

    @Transactional
    public void changePassword(Long id, ChangePasswordRequest r) {
        User u = load(id);
        if (!encoder.matches(r.currentPassword(), u.getPasswordHash())) {
            throw ApiException.badRequest("Your current password is wrong.");
        }
        u.setPasswordHash(encoder.encode(r.newPassword()));
    }

    private User load(Long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
