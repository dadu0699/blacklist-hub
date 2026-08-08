package com.blacklisthub.slack.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.blacklisthub.slack.util.CommandParser.Parsed;

class SlackMessageFormatterTest {

    @Test
    void prependsMentionAndKeepsOkEmojiWithReason() {
        String out = SlackMessageFormatter.prettyResultForChannel(
                "U1", ":white_check_mark: Added `1.2.3.4`", new Parsed("add", List.of("1.2.3.4"), "abusive traffic"));
        assertThat(out).isEqualTo(":white_check_mark: <@U1> Added `1.2.3.4`\n reason: abusive traffic");
    }

    @Test
    void editUsesNewReasonLabel() {
        String out = SlackMessageFormatter.prettyResultForChannel(
                "U1", ":white_check_mark: Updated `1.2.3.4` reason", new Parsed("edit", List.of("1.2.3.4"), "better"));
        assertThat(out).isEqualTo(":white_check_mark: <@U1> Updated `1.2.3.4` reason\n new reason: better");
    }

    @Test
    void preservesWarningEmojiAndOmitsReasonWhenAbsent() {
        String out = SlackMessageFormatter.prettyResultForChannel(
                "U1", ":warning: Invalid IP: `999`", new Parsed("add", List.of("999"), ""));
        assertThat(out).isEqualTo(":warning: <@U1> Invalid IP: `999`");
    }

    @Test
    void defaultsToOkEmojiWhenRawHasNoKnownPrefix() {
        String out = SlackMessageFormatter.prettyResultForChannel(
                "U1", "```\n1.2.3.4\n```", new Parsed("list", List.of(), ""));
        assertThat(out).isEqualTo(":white_check_mark: <@U1> ```\n1.2.3.4\n```");
    }

    @Test
    void doesNotAppendReasonForSubcommandsOutsideTheAllowedSet() {
        String out = SlackMessageFormatter.prettyResultForChannel(
                "U1", ":white_check_mark: done", new Parsed("bulk", List.of("a"), "ignored tail"));
        assertThat(out).isEqualTo(":white_check_mark: <@U1> done");
    }
}
