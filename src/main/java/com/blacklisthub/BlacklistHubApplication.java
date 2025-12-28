package com.blacklisthub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({ com.blacklisthub.slack.config.SlackProps.class })
public class BlacklistHubApplication {

	public static void main(String[] args) {
		SpringApplication.run(BlacklistHubApplication.class, args);
	}

}
