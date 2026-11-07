package com.tulas.canteen.security;

import com.tulas.canteen.domain.Role;
import com.tulas.canteen.domain.User;

/** The logged-in user, as stored in the security context for one request. */
public record AuthUser(Long id, String name, String email, Role role) {

    public static AuthUser of(User u) {
        return new AuthUser(u.getId(), u.getName(), u.getEmail(), u.getRole());
    }

    public boolean isStaffOrAdmin() {
        return role == Role.STAFF || role == Role.ADMIN;
    }
}
