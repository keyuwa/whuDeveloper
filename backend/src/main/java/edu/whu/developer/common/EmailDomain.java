package edu.whu.developer.common;

import java.util.Locale;

public final class EmailDomain {

    private EmailDomain() {
    }

    public static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isStudent(String email) {
        return domain(email).endsWith(".edu.cn");
    }

    public static boolean isPublisher(String email) {
        return domain(email).endsWith(".whu.edu.cn");
    }

    /** 是否校园邮箱（学生 .edu.cn / 发布方 .whu.edu.cn），用于前端提示，不强制。 */
    public static boolean isCampusRecommended(String email, boolean publisher) {
        return publisher ? isPublisher(email) : isStudent(email);
    }

    private static String domain(String email) {
        String normalized = normalize(email);
        int at = normalized.lastIndexOf('@');
        if (at < 0 || at == normalized.length() - 1) {
            return "";
        }
        return normalized.substring(at + 1);
    }
}
