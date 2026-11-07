package com.tulas.canteen.service;

import com.tulas.canteen.config.ApiException;
import com.tulas.canteen.domain.Role;
import com.tulas.canteen.domain.User;
import com.tulas.canteen.dto.AuthDtos.*;
import com.tulas.canteen.repo.UserRepository;
import com.tulas.canteen.security.JwtService;
import com.tulas.canteen.security.LoginGuard;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final LoginGuard guard;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt, LoginGuard guard) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.guard = guard;
    }

    /** Public sign-up always creates a STUDENT. Staff accounts are created by the admin. */
    @Transactional
    public AuthResponse register(RegisterRequest r, String ip) {
        guard.checkSignup(ip);
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
    public AuthResponse login(LoginRequest r, String ip) {
        String email = r.email().trim();
        guard.checkLogin(email, ip);
        User u = users.findByEmailIgnoreCase(email)
                .filter(x -> encoder.matches(r.password(), x.getPasswordHash()))
                .orElse(null);
        if (u == null) {
            guard.loginFailed(email, ip);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Wrong email or password.");
        }
        guard.loginSucceeded(email);
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

    /**
     * Changes the password and logs out every other device (their tokens carry the old version).
     * Returns a fresh token so the device that made the change stays logged in.
     */
    @Transactional
    public AuthResponse changePassword(Long id, ChangePasswordRequest r) {
        User u = load(id);
        if (!encoder.matches(r.currentPassword(), u.getPasswordHash())) {
            throw ApiException.badRequest("Your current password is wrong.");
        }
        if (encoder.matches(r.newPassword(), u.getPasswordHash())) {
            throw ApiException.badRequest("The new password must be different from the current one.");
        }
        u.setPasswordHash(encoder.encode(r.newPassword()));
        u.bumpTokenVersion();
        return new AuthResponse(jwt.issue(u), UserView.of(u));
    }

    /**
     * Admin sets a temporary password for someone who forgot theirs (there's no email reset).
     * All their existing logins stop working. Returns the temporary password to hand over.
     */
    @Transactional
    public String resetPassword(Long id) {
        User u = load(id);
        String temp = TempPassword.generate();
        u.setPasswordHash(encoder.encode(temp));
        u.bumpTokenVersion();
        return temp;
    }

    /** Admin "log out everywhere" for a user, e.g. a lost phone. */
    @Transactional
    public void logoutEverywhere(Long id) {
        load(id).bumpTokenVersion();
    }

    public User get(Long id) {
        return load(id);
    }

    private User load(Long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
