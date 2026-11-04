package edu.whu.developer.ability;

import edu.whu.developer.auth.User;

import java.util.Locale;
import java.util.Set;

public final class AbilityLabels {

    public static final Set<String> LEVELS = Set.of("LV0", "LV1", "LV2", "LV3", "LV4", "LV5");

    private AbilityLabels() {
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "LV0";
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if (value.matches("L[1-5]")) {
            return "LV" + value.substring(1);
        }
        if (LEVELS.contains(value)) {
            return value;
        }
        throw new IllegalArgumentException("非法能力等级: " + raw);
    }

    public static String label(String level, boolean apprentice, boolean mgmt) {
        String base = normalize(level);
        StringBuilder sb = new StringBuilder(base);
        if (apprentice) {
            sb.append("见习");
        }
        if (mgmt) {
            sb.append("·M");
        }
        return sb.toString();
    }

    public static String label(User user) {
        return label(user.getAbilityLevel() == null ? "LV0" : user.getAbilityLevel(),
                user.isAbilityApprentice(),
                user.isAbilityMgmt());
    }

    public static int rankValue(String level) {
        return switch (normalize(level)) {
            case "LV1" -> 1;
            case "LV2" -> 2;
            case "LV3" -> 3;
            case "LV4" -> 4;
            case "LV5" -> 5;
            default -> 0;
        };
    }

    public static boolean canJudge(User user) {
        if (user.getRole() == edu.whu.developer.auth.Role.ADMIN) {
            return true;
        }
        if (user.getRole() != edu.whu.developer.auth.Role.STUDENT) {
            return false;
        }
        int rank = rankValue(user.getAbilityLevel());
        return rank >= 4;
    }
}
