package com.ipblocklist.api.slack.util;

import java.util.List;

public class IpCommandParser {

    public record Parsed(String sub, List<String> args, String tail) {
    }

    public static Parsed parse(String text) {
        if (text == null || text.isBlank()) {
            return new Parsed("", List.of(), "");
        }

        // split with limit=3 -> [sub] [first-arg-or-empty]
        // [the-rest-as-a-single-string]
        String[] parts = text.trim().split("\\s+", 3);

        String sub = parts[0].toLowerCase();

        // args: only real arguments (exclude the command itself)
        if (parts.length == 1) {
            return new Parsed(sub, List.of(), "");
        } else if (parts.length == 2) {
            // one argument, no tail
            return new Parsed(sub, List.of(parts[1]), "");
        } else {
            // two or more tokens -> first arg + tail = "everything after the first arg"
            return new Parsed(sub, List.of(parts[1]), parts[2]);
        }
    }
}
