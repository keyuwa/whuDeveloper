package edu.whu.developer.auth;

public record UserResponse(
        Long id,
        String email,
        String role,
        String displayName,
        String realName,
        String abilityLevel,
        String status
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                user.getDisplayName(),
                user.getRealName(),
                user.getAbilityLevel(),
                user.getStatus());
    }
}
