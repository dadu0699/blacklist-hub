package com.blacklisthub.slack.util;

import com.blacklisthub.slack.util.CommandParser.Parsed;

public final class CommandTextUtils {
    private CommandTextUtils() {
    }

    public static String firstArg(Parsed p) {
        return (p.args() == null || p.args().isEmpty()) ? "" : p.args().get(0);
    }

    public static String tailOrNull(Parsed p) {
        return (p.tail() == null || p.tail().isBlank()) ? null : p.tail();
    }

    public static String capitalize(String s) {
        if (s == null || s.isBlank())
            return "";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public static String safe(String s) {
        return s == null ? "" : s;
    }
}