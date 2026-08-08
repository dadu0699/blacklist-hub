package com.blacklisthub.slack.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class IocUtilsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // --- isValidIp (T-04): lexical only, no DNS, hostnames rejected ---

    @Test
    void acceptsValidIpv4AndIpv6Literals() {
        assertThat(IocUtils.isValidIp("203.0.113.5")).isTrue();
        assertThat(IocUtils.isValidIp("2001:db8::1")).isTrue();
        assertThat(IocUtils.isValidIp("::1")).isTrue();
    }

    @Test
    void rejectsHostnamesAndGarbage() {
        assertThat(IocUtils.isValidIp("evil.example.com")).isFalse();
        assertThat(IocUtils.isValidIp("localhost")).isFalse();
        assertThat(IocUtils.isValidIp("256.256.256.256")).isFalse();
        assertThat(IocUtils.isValidIp("not-an-ip")).isFalse();
    }

    @Test
    void rejectsNullAndBlankIp() {
        assertThat(IocUtils.isValidIp(null)).isFalse();
        assertThat(IocUtils.isValidIp("   ")).isFalse();
    }

    // --- isValidHash (T-06): exact MD5/SHA-1/SHA-256 lengths ---

    @Test
    void acceptsStandardHashLengths() {
        assertThat(IocUtils.isValidHash("d41d8cd98f00b204e9800998ecf8427e")).isTrue(); // MD5 (32)
        assertThat(IocUtils.isValidHash("da39a3ee5e6b4b0d3255bfef95601890afd80709")).isTrue(); // SHA-1 (40)
        assertThat(IocUtils.isValidHash(
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")).isTrue(); // SHA-256 (64)
    }

    @Test
    void rejectsNonStandardHashLengthsAndNonHex() {
        assertThat(IocUtils.isValidHash("a".repeat(31))).isFalse();
        assertThat(IocUtils.isValidHash("a".repeat(33))).isFalse();
        assertThat(IocUtils.isValidHash("a".repeat(50))).isFalse();
        assertThat(IocUtils.isValidHash("a".repeat(63))).isFalse();
        assertThat(IocUtils.isValidHash("z".repeat(32))).isFalse(); // non-hex
        assertThat(IocUtils.isValidHash(null)).isFalse();
        assertThat(IocUtils.isValidHash("  ")).isFalse();
    }

    // --- isValidUrl (T-06): only http/https with a host ---

    @Test
    void acceptsHttpAndHttpsUrls() {
        assertThat(IocUtils.isValidUrl("http://example.com/path")).isTrue();
        assertThat(IocUtils.isValidUrl("https://example.com")).isTrue();
    }

    @Test
    void rejectsDangerousOrNonWebSchemes() {
        assertThat(IocUtils.isValidUrl("javascript:alert(1)")).isFalse();
        assertThat(IocUtils.isValidUrl("file:///etc/passwd")).isFalse();
        assertThat(IocUtils.isValidUrl("data:text/html,<script>")).isFalse();
        assertThat(IocUtils.isValidUrl("ftp://example.com/f")).isFalse();
        assertThat(IocUtils.isValidUrl("/relative/path")).isFalse();
        assertThat(IocUtils.isValidUrl(null)).isFalse();
        assertThat(IocUtils.isValidUrl("   ")).isFalse();
    }

    // --- normalizeUrl (unchanged behavior, guarded here for regressions) ---

    @Test
    void normalizesDefangedUrls() {
        assertThat(IocUtils.normalizeUrl("hxxps://evil[.]com")).isEqualTo("https://evil.com");
        assertThat(IocUtils.normalizeUrl("hxxp://a[.]b[.]c")).isEqualTo("http://a.b.c");
        assertThat(IocUtils.normalizeUrl(null)).isNull();
    }

    // --- jsonKV (T-07): produce valid, parseable JSON regardless of the value ---

    @Test
    void jsonKVEmitsSimpleAndNullAndUnquotedForms() {
        assertThat(IocUtils.jsonKV("reason", "hello", true)).isEqualTo("\"reason\":\"hello\"");
        assertThat(IocUtils.jsonKV("reason", null, true)).isEqualTo("\"reason\":null");
        assertThat(IocUtils.jsonKV("active", "1", false)).isEqualTo("\"active\":1");
    }

    @Test
    void jsonKVProducesValidJsonForValuesWithSpecialCharacters() throws Exception {
        // Backslashes, quotes, newlines and tabs used to break the audit insert.
        String tricky = "he said \"hi\"\nwith a backslash \\ path C:\\tmp and tab\tend";
        String jsonObject = "{" + IocUtils.jsonKV("reason", tricky, true) + "}";

        JsonNode node = MAPPER.readTree(jsonObject); // throws if the JSON is malformed
        assertThat(node.get("reason").asText()).isEqualTo(tricky);
    }

    @Test
    void jsonKVEscapesControlCharacters() throws Exception {
        String withControl = "line1\u0001line2";
        String jsonObject = "{" + IocUtils.jsonKV("reason", withControl, true) + "}";

        JsonNode node = MAPPER.readTree(jsonObject);
        assertThat(node.get("reason").asText()).isEqualTo(withControl);
    }
}
