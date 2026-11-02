package edu.whu.developer.auth;

public record AuthResponse(String token, UserResponse user) {
}
