package com.blacklisthub.slack.util;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

public class IocUtils {

    // Hex strings of the exact lengths of MD5 (32), SHA-1 (40) and SHA-256 (64).
    private static final Pattern HASH_PATTERN = Pattern
            .compile("^([a-fA-F0-9]{32}|[a-fA-F0-9]{40}|[a-fA-F0-9]{64})$");

    // Simple domain regex
    private static final Pattern DOMAIN_PATTERN = Pattern
            .compile("^([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,63}$");

    public static boolean isValidIp(String ip) {
        if (ip == null || ip.isBlank())
            return false;
        try {
            // ofLiteral parses IPv4/IPv6 literals only: no DNS resolution and
            // hostnames are rejected (unlike the previous getByName call).
            InetAddress.ofLiteral(ip);
            return true;
        } catch (IllegalArgumentException e) {
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
            String scheme = uri.getScheme();
            // Only http/https are accepted; a host must be present. This rejects
            // dangerous schemes such as javascript:, file:, data: and ftp:.
            return uri.getHost() != null
                    && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
        } catch (URISyntaxException e) {
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