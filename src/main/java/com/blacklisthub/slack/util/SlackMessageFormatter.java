package com.blacklisthub.slack.util;

import static com.blacklisthub.slack.util.CommandTextUtils.capitalize;
import static com.blacklisthub.slack.util.CommandTextUtils.firstArg;
import static com.blacklisthub.slack.util.CommandTextUtils.safe;
import static com.blacklisthub.slack.util.CommandTextUtils.tailOrNull;

import com.blacklisthub.slack.util.CommandParser.Parsed;

public final class SlackMessageFormatter {
    private SlackMessageFormatter() {
    }

    private static final String OK = ":white_check_mark:";
    private static final String ERR = ":x:";
    private static final String WARN = ":warning:";

    /**
     * Builds the visible audit log message.
     * 
     * @param userId      The user who ran the command
     * @param channelId   The channel
     * @param commandName The command that was run (e.g., "/ip", "/hash")
     * @param parsed      The parsed command arguments
     */
    public static String buildAuditMessage(String userId, String channelId, String commandName, Parsed parsed) {
        final String action = capitalize(parsed.sub());
        final String iocValue = firstArg(parsed);
        final String reason = tailOrNull(parsed);

        // Get the IoC type from the command, e.g., "IP", "HASH"
        String iocType = commandName.replace("/", "").toUpperCase();

        StringBuilder sb = new StringBuilder()
                .append(":memo: <@").append(userId).append("> ")
                .append(action); // e.g., "Add"

        // Add the type, e.g., "HASH"
        if (!iocType.isBlank()) {
            sb.append(" *").append(iocType).append("*");
        }

        // Add the value, e.g., "`123...`"
        if (!iocValue.isBlank()) {
            sb.append(" `").append(iocValue).append("`");
        }

        if (reason != null) {
            if ("edit".equalsIgnoreCase(parsed.sub())) {
                sb.append("\n *new reason:* ").append(reason);
            } else {
                sb.append("\n *reason:* ").append(reason);
            }
        }
        return sb.toString();
    }

    /**
     * Formats the final "in_channel" response.
     * (This implementation remains unchanged from the original)
     */
    public static String prettyResultForChannel(String userId, String raw, Parsed parsed) {
        String msg = safe(raw).trim();

        String emoji;
        String rest;

        if (msg.startsWith(OK)) {
            emoji = OK;
            rest = msg.substring(OK.length()).trim();
        } else if (msg.startsWith(ERR)) {
            emoji = ERR;
            rest = msg.substring(ERR.length()).trim();
        } else if (msg.startsWith(WARN)) {
            emoji = WARN;
            rest = msg.substring(WARN.length()).trim();
        } else {
            emoji = OK;
            rest = msg;
        }

        StringBuilder out = new StringBuilder()
                .append(String.format("%s <@%s> %s", emoji, userId, rest));

        String reason = tailOrNull(parsed);
        String sub = parsed.sub() == null ? "" : parsed.sub().toLowerCase();

        if (reason != null
                && (sub.equals("add") || sub.equals("deactivate") || sub.equals("reactivate") || sub.equals("edit"))) {
            if (sub.equals("edit")) {
                out.append("\n new reason: ").append(reason);
            } else {
                out.append("\n reason: ").append(reason);
            }
        }

        return out.toString();
    }
}