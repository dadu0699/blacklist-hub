package com.blacklisthub.slack.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "slack")
public record SlackProps(
        String appToken, // xapp-***
        String botToken, // xoxb-***
        String signingSecret // optional if does not use Events API
) {
}
