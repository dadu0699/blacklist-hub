package com.ipblocklist.api.slack;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;

@Configuration
public class SlackClientsConfig {

    @Bean
    MethodsClient slackMethodsClient(SlackProps props) {
        return Slack.getInstance().methods(props.botToken());
    }
}
