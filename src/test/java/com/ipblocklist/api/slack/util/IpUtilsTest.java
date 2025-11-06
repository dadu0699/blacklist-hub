package com.ipblocklist.api.slack.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IpUtilsTest {

    @Test
    void testValidIpv4() {
        assertTrue(IpUtils.isValidIp("192.168.1.1"));
    }

    @Test
    void testInvalidIp() {
        assertFalse(IpUtils.isValidIp("999.999.999.999"));
        assertFalse(IpUtils.isValidIp("not-an-ip"));
    }

    @Test
    void testJsonKVQuoted() {
        String kv = IpUtils.jsonKV("ip", "1.1.1.1", true);
        assertEquals("\"ip\":\"1.1.1.1\"", kv);
    }

    @Test
    void testJsonKVNull() {
        assertEquals("\"ip\":null", IpUtils.jsonKV("ip", null, true));
    }
}
