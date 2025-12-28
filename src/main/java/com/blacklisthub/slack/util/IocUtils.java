package com.blacklisthub.slack.util;

import java.net.InetAddress;
import java.net.URI;
import java.util.regex.Pattern;

public class IocUtils {

    // Regex for SHA-256, SHA-1, MD5
    private static final Pattern HASH_PATTERN = Pattern.compile("^[a-fA-F0-9]{32,64}$");

    // Simple domain regex
    private static final Pattern DOMAIN_PATTERN = Pattern
            .compile("^([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,63}$");

    public static boolean isValidIp(String ip) {
        if (ip == null || ip.isBlank())
            return false;
        try {
            InetAddress.getByName(ip);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isValidHash(String hash) {
        if (hash == null || hash.isBlank())
            return false;
        return HASH_PATTERN.matcher(hash).matches();
    }

    public static boolean isValidDomain(String domain) {
        if (domain == null || domain.isBlank())
            return false;
        return DOMAIN_PATTERN.matcher(domain).matches();
    }

    public static String normalizeUrl(String url) {
        if (url == null)
            return null;

        return url.replace("hxxp://", "http://")
                .replace("hxxps://", "https://")
                .replace("[.]", ".");
    }

    public static boolean isValidUrl(String url) {
        if (url == null || url.isBlank())
            return false;
        try {
            URI uri = new URI(url);
            return uri.isAbsolute() && uri.getScheme() != null;
        } catch (Exception e) {
            return false;
        }
    }

    public static String jsonKV(String k, String vOrNull, boolean quote) {
        if (vOrNull == null)
            return "\"" + k + "\":null";

        String safeVal = quote ? "\"" + vOrNull.replace("\"", "\\\"") + "\"" : vOrNull;
        return "\"" + k + "\":" + safeVal;
    }
}