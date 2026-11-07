package com.tulas.canteen.web;

import com.tulas.canteen.dto.AuthDtos.*;
import com.tulas.canteen.security.AuthUser;
import com.tulas.canteen.security.LoginGuard;
import com.tulas.canteen.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest r, HttpServletRequest req) {
        return auth.register(r, LoginGuard.clientIp(req));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest r, HttpServletRequest req) {
        return auth.login(r, LoginGuard.clientIp(req));
    }

    @GetMapping("/me")
    public UserView me(@AuthenticationPrincipal AuthUser me) {
        return auth.me(me.id());
    }

    @PutMapping("/me")
    public UserView updateProfile(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody UpdateProfileRequest r) {
        return auth.updateProfile(me.id(), r);
    }

    /** Returns a new token: the password change logs out every other device. */
    @PostMapping("/password")
    public AuthResponse changePassword(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody ChangePasswordRequest r) {
        return auth.changePassword(me.id(), r);
    }
}
