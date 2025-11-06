package com.ipblocklist.api.slack.util;

import java.net.InetAddress;

public class IpUtils {

    public static boolean isValidIp(String ip) {
        try {
            return InetAddress.getByName(ip) != null;
        } catch (Exception e) {
            return false;
        }
    }

    public static String jsonKV(String k, String vOrNull, boolean quote) {
        if (vOrNull == null)
            return "\"" + k + "\":null";
        return "\"" + k + "\":" + (quote ? "\"" + vOrNull.replace("\"", "\\\"") + "\"" : vOrNull);
    }
}
