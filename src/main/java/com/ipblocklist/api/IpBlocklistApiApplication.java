package com.ipblocklist.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({ com.ipblocklist.api.slack.config.SlackProps.class })
public class IpBlocklistApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(IpBlocklistApiApplication.class, args);
	}

}
