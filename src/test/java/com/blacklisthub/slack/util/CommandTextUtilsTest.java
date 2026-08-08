package com.blacklisthub.slack.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.blacklisthub.slack.util.CommandParser.Parsed;

class CommandTextUtilsTest {

    @Test
    void firstArgReturnsEmptyWhenNoArgs() {
        assertThat(CommandTextUtils.firstArg(new Parsed("list", List.of(), ""))).isEmpty();
        assertThat(CommandTextUtils.firstArg(new Parsed("add", List.of("1.2.3.4"), ""))).isEqualTo("1.2.3.4");
    }

    @Test
    void tailOrNullTreatsBlankAsNull() {
        assertThat(CommandTextUtils.tailOrNull(new Parsed("add", List.of("x"), ""))).isNull();
        assertThat(CommandTextUtils.tailOrNull(new Parsed("add", List.of("x"), "   "))).isNull();
        assertThat(CommandTextUtils.tailOrNull(new Parsed("add", List.of("x"), "reason"))).isEqualTo("reason");
    }

    @Test
    void capitalizeFirstLetterOrEmpty() {
        assertThat(CommandTextUtils.capitalize(null)).isEmpty();
        assertThat(CommandTextUtils.capitalize("")).isEmpty();
        assertThat(CommandTextUtils.capitalize("add")).isEqualTo("Add");
        assertThat(CommandTextUtils.capitalize("a")).isEqualTo("A");
    }

    @Test
    void safeNeverReturnsNull() {
        assertThat(CommandTextUtils.safe(null)).isEmpty();
        assertThat(CommandTextUtils.safe("x")).isEqualTo("x");
    }
}
