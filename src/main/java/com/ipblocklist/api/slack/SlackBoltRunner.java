package com.ipblocklist.api.slack;

import org.springframework.stereotype.Component;

import com.ipblocklist.api.slack.config.SlackProps;
import com.ipblocklist.api.slack.service.ChannelAccessService;
import com.ipblocklist.api.slack.service.IpCommandService;
import com.ipblocklist.api.slack.util.IpCommandParser;
import com.ipblocklist.api.slack.util.SlackMessageFormatter;
import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;
import com.slack.api.bolt.socket_mode.SocketModeApp;
import com.slack.api.methods.MethodsClient;
import com.slack.api.model.event.AppMentionEvent;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlackBoltRunner {

    private final SlackProps props;
    private final IpCommandService ipCommandService;
    private final MethodsClient slackClient;
    private final ChannelAccessService channelAccessService;

    private SocketModeApp socketModeApp;

    @PostConstruct
    public void start() throws Exception {
        // Bolt app config (bot token for Web API, optional signing secret)
        AppConfig config = AppConfig.builder()
                .singleTeamBotToken(props.botToken())
                .signingSecret(props.signingSecret() == null ? "" : props.signingSecret())
                .build();

        App app = new App(config);

        app.command("/ip", (req, ctx) -> {
            final String text = req.getPayload().getText();
            final String channelId = req.getPayload().getChannelId();
            final String userId = req.getPayload().getUserId();
            final String teamId = req.getPayload().getTeamId();

            log.info("Received /ip '{}' from user={} in channel={}", text, userId, channelId);

            // Ack immediately (avoid Slack 3s timeout)
            ctx.ack(":hourglass_flowing_sand: processing…");

            // Check whitelist asynchronously
            channelAccessService.isChannelAllowed(channelId).subscribe(
                    allowed -> {
                        if (!allowed) {
                            try {
                                ctx.respond(r -> r
                                        .responseType("ephemeral")
                                        .text(":no_entry_sign: Commands are not allowed in this channel."));
                            } catch (Exception e) {
                                log.error("Failed to respond unauthorized channel", e);
                            }
                            return;
                        }

                        // Parse and execute via service
                        var parsed = IpCommandParser.parse(text);

                        ipCommandService.execute(parsed, userId, teamId, channelId).subscribe(
                                resultMessage -> {
                                    try {
                                        // 1) Visible audit entry (includes reason when present)
                                        String auditMsg = SlackMessageFormatter.buildAuditMessage(
                                                userId, channelId, parsed);
                                        slackClient.chatPostMessage(r -> r.channel(channelId).text(auditMsg));

                                        // 2) Public (in_channel) response with pretty formatting + reason
                                        String publicMsg = SlackMessageFormatter.prettyResultForChannel(
                                                userId, resultMessage, parsed);
                                        ctx.respond(r -> r
                                                .responseType("in_channel")
                                                .text(publicMsg));
                                    } catch (Exception e) {
                                        log.error("Failed to post Slack responses", e);
                                        try {
                                            ctx.respond(":x: Failed to post response: " + e.getMessage());
                                        } catch (Exception ignore) {
                                            /* swallow */
                                        }
                                    }
                                },
                                err -> {
                                    log.error("Error executing /ip command", err);
                                    try {
                                        ctx.respond(":x: Internal error executing command: " + err.getMessage());
                                    } catch (Exception ignore) {
                                        /* swallow */
                                    }
                                });
                    },
                    err -> {
                        log.error("Error checking channel whitelist", err);
                        try {
                            ctx.respond(":x: Error checking channel whitelist: " + err.getMessage());
                        } catch (Exception ignore) {
                            /* swallow */ }
                    });

            // We already acked above
            return ctx.ack();
        });

        // Optional: respond to @mentions for a quick health check
        app.event(AppMentionEvent.class, (payload, ctx) -> {
            String channelId = payload.getEvent().getChannel();
            log.info("App mentioned in channel {}", channelId);
            ctx.say("👋 I'm alive and managing the IP blocklist commands.");
            return ctx.ack();
        });

        // Start Socket Mode using the app-level token (xapp-***)
        socketModeApp = new SocketModeApp(props.appToken(), app);
        socketModeApp.startAsync();

        log.info("✅ Slack Bolt runner started successfully in Socket Mode.");
    }

    @PreDestroy
    public void stop() throws Exception {
        if (socketModeApp != null) {
            try {
                socketModeApp.close();
            } catch (Exception e) {
                log.warn("Error closing SocketModeApp", e);
            }
        }
    }

}
