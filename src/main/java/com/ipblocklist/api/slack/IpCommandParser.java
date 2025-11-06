package com.ipblocklist.api.slack;

import java.util.List;

public class IpCommandParser {

    public record Parsed(String sub, List<String> args, String tail) {
    }

    public static Parsed parse(String text) {
        if (text == null || text.isBlank())
            return new Parsed("", List.of(), "");
        var parts = text.trim().split("\\s+", 3);
        var sub = parts[0].toLowerCase();
        var ip = parts.length > 1 ? parts[1] : "";
        var tail = parts.length > 2 ? parts[2] : "";
        return new Parsed(sub, List.of(ip), tail);
    }
}
