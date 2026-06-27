package com.hisaberp.shared.i18n;

import java.util.regex.Pattern;

/**
 * Mask PII before it reaches log files. Apply at every log site that handles
 * customer/employee data. See ADR-003.
 */
public final class LogSanitizer {

    private static final Pattern EMAIL = Pattern.compile("([A-Za-z0-9._%+-])([A-Za-z0-9._%+-]*)(@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})");
    private static final Pattern PHONE = Pattern.compile("(\\+?\\d{1,3}[\\s-]?)?(\\d{2,4}[\\s-]?){2,4}\\d{2,4}");

    private LogSanitizer() {}

    public static String sanitize(String input) {
        if (input == null || input.isEmpty()) return input;
        String masked = EMAIL.matcher(input).replaceAll("$1***$3");
        masked = PHONE.matcher(masked).replaceAll(LogSanitizer::maskPhone);
        return masked;
    }

    private static String maskPhone(java.util.regex.MatchResult match) {
        String s = match.group();
        if (s.length() < 4) return s;
        return s.substring(0, 2) + "*".repeat(Math.max(0, s.length() - 4)) + s.substring(s.length() - 2);
    }
}
