package com.blacklisthub.slack.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.blacklisthub.slack.util.CommandParser.Parsed;

class CommandParserTest {

    @Test
    void nullOrBlankYieldsEmptyParsed() {
        for (String in : new String[] { null, "", "   " }) {
            Parsed p = CommandParser.parse(in);
            assertThat(p.sub()).isEmpty();
            assertThat(p.args()).isEmpty();
            assertThat(p.tail()).isEmpty();
        }
    }

    @Test
    void subOnly() {
        Parsed p = CommandParser.parse("list");
        assertThat(p.sub()).isEqualTo("list");
        assertThat(p.args()).isEmpty();
        assertThat(p.tail()).isEmpty();
    }

    @Test
    void subAndSingleArgumentNoTail() {
        Parsed p = CommandParser.parse("add 1.2.3.4");
        assertThat(p.sub()).isEqualTo("add");
        assertThat(p.args()).containsExactly("1.2.3.4");
        assertThat(p.tail()).isEmpty();
    }

    @Test
    void subArgumentAndTailKeepsRestAsSingleString() {
        Parsed p = CommandParser.parse("add 1.2.3.4 abusive traffic from C2");
        assertThat(p.sub()).isEqualTo("add");
        assertThat(p.args()).containsExactly("1.2.3.4");
        assertThat(p.tail()).isEqualTo("abusive traffic from C2");
    }

    @Test
    void subIsLowercasedAndSurroundingWhitespaceIgnored() {
        Parsed p = CommandParser.parse("   ADD   1.2.3.4   some reason   ");
        assertThat(p.sub()).isEqualTo("add");
        assertThat(p.args()).containsExactly("1.2.3.4");
        assertThat(p.tail()).isEqualTo("some reason");
    }
}
