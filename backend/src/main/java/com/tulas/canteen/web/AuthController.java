package com.tulas.canteen.web;

import com.tulas.canteen.dto.AuthDtos.*;
import com.tulas.canteen.security.AuthUser;
import com.tulas.canteen.service.AuthService;
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
    public AuthResponse register(@Valid @RequestBody RegisterRequest r) {
        return auth.register(r);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest r) {
        return auth.login(r);
    }

    @GetMapping("/me")
    public UserView me(@AuthenticationPrincipal AuthUser me) {
        return auth.me(me.id());
    }

    @PutMapping("/me")
    public UserView updateProfile(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody UpdateProfileRequest r) {
        return auth.updateProfile(me.id(), r);
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody ChangePasswordRequest r) {
        auth.changePassword(me.id(), r);
    }
}
