package com.ipblocklist.api.slack.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IpCommandParserTest {

    @Test
    void testParseSimpleAdd() {
        var p = IpCommandParser.parse("add 1.1.1.1 reason text");
        assertEquals("add", p.sub());
        assertEquals("1.1.1.1", p.args().get(0));
        assertEquals("reason text", p.tail());
    }

    @Test
    void testParseEmptyText() {
        var p = IpCommandParser.parse("");
        assertEquals("", p.sub());
        assertTrue(p.args().isEmpty());
        assertEquals("", p.tail());
    }

    @Test
    void testParseOnlyCommand() {
        var p = IpCommandParser.parse("list");
        assertEquals("list", p.sub());
        assertTrue(p.args().isEmpty());
    }
}
