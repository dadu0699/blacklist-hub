package com.ipblocklist.api.slack.util;

import static com.ipblocklist.api.slack.util.CommandTextUtils.capitalize;
import static com.ipblocklist.api.slack.util.CommandTextUtils.firstArg;
import static com.ipblocklist.api.slack.util.CommandTextUtils.safe;
import static com.ipblocklist.api.slack.util.CommandTextUtils.tailOrNull;

import com.ipblocklist.api.slack.util.IpCommandParser.Parsed;

public final class SlackMessageFormatter {
    private SlackMessageFormatter() {
    }

    private static final String OK = ":white_check_mark:";
    private static final String ERR = ":x:";
    private static final String WARN = ":warning:";

    /**
     * Example:
     * :memo: <@U123> Add `10.10.0.5`
     * reason: first entry
     */
    public static String buildAuditMessage(String userId, String channelId, Parsed parsed) {
        final String action = capitalize(parsed.sub());
        final String ip = firstArg(parsed);
        final String reason = tailOrNull(parsed);

        StringBuilder sb = new StringBuilder()
                .append(":memo: <@").append(userId).append("> ")
                .append(action);

        if (!ip.isBlank()) {
            sb.append(" `").append(ip).append("`");
        }

        if (reason != null) {
            // For edit, clarify that it’s the new reason
            if ("edit".equalsIgnoreCase(parsed.sub())) {
                sb.append("\n *new reason:* ").append(reason);
            } else {
                sb.append("\n *reason:* ").append(reason);
            }
        }
        return sb.toString();
    }

    /**
     * Formats a nice in_channel message from the service result.
     * - Preserves initial emoji from the service (OK/ERR/WARN) or defaults to OK
     * - Prefix with actor mention
     * - Append "reason" line when present and the subcommand supports it
     *
     * Example:
     * :white_check_mark: <@U123> Added `10.10.0.5`
     * reason: first entry
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

        // Append reason if present (and command matches)
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
