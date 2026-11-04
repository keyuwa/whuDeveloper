package edu.whu.developer.auth;

public record UserResponse(
        Long id,
        String email,
        String role,
        String displayName,
        String realName,
        String school,
        String college,
        String major,
        String studentIdentity,
        String bio,
        String resumeFileUrl,
        String resumeFileName,
        String abilityLevel,
        boolean abilityApprentice,
        boolean abilityMgmt,
        String abilityLabel,
        int points,
        String status,
        boolean resumeReady
) {
    public static UserResponse from(User user) {
        String level = user.getAbilityLevel() == null ? "LV0" : user.getAbilityLevel();
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                user.getDisplayName(),
                user.getRealName(),
                user.getSchool(),
                user.getCollege(),
                user.getMajor(),
                user.getStudentIdentity(),
                user.getBio(),
                user.getResumeFileUrl(),
                user.getResumeFileName(),
                level,
                user.isAbilityApprentice(),
                user.isAbilityMgmt(),
                edu.whu.developer.ability.AbilityLabels.label(level, user.isAbilityApprentice(), user.isAbilityMgmt()),
                user.getPoints(),
                user.getStatus(),
                isResumeReady(user));
    }

    public static boolean isResumeReady(User user) {
        if (user.getRole() != Role.STUDENT) {
            return true;
        }
        return filled(user.getRealName())
                && filled(user.getSchool())
                && filled(user.getCollege())
                && filled(user.getMajor())
                && filled(user.getStudentIdentity())
                && filled(user.getBio())
                && user.getBio().trim().length() >= 20;
    }

    private static boolean filled(String value) {
        return value != null && !value.isBlank();
    }
}
