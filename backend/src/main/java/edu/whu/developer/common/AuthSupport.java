package edu.whu.developer.common;

import edu.whu.developer.auth.Role;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

public final class AuthSupport {

    private AuthSupport() {
    }

    public static AuthUser current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        return user;
    }

    public static AuthUser requireRole(Role role) {
        AuthUser user = current();
        if (user.getRole() != role) {
            String message = role == Role.PUBLISHER ? "只有发布者可以执行此操作" : "只有学生可以执行此操作";
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
        }
        return user;
    }
}
