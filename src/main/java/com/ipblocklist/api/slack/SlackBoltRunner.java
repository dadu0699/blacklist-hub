package com.ipblocklist.api.slack;

import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;
import com.slack.api.bolt.socket_mode.SocketModeApp;
import com.slack.api.socket_mode.SocketModeClient;
import com.slack.api.webhook.WebhookResponse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlackBoltRunner {

    private final SlackProps props;
    private final IpCommandService ipService;

    private SocketModeApp socketModeApp;

    @PostConstruct
    public void start() throws Exception {
        // Build a proper AppConfig; the lambda-style constructor does not exist
        AppConfig config = AppConfig.builder()
                .singleTeamBotToken(props.botToken())
                // signingSecret is optional if you only use Socket Mode (no Events API)
                .signingSecret(props.signingSecret() == null ? "" : props.signingSecret())
                .build();

        App app = new App(config);

        // Register the slash command: /ip
        app.command("/ip", (req, ctx) -> {
            final String userId = req.getPayload().getUserId();
            final String teamId = req.getPayload().getTeamId();
            final String text = req.getPayload().getText();

            // ACK immediately to avoid the 3s timeout in Slack
            // (You can return this response; further messages go via respond())
            ctx.ack(":hourglass_flowing_sand: processing…");

            var parsed = IpCommandService.parse(text);
            switch (parsed.sub()) {
                case "add" -> {
                    final String ip = parsed.args().isEmpty() ? "" : parsed.args().get(0);
                    final String reason = parsed.tail().isBlank() ? null : parsed.tail();
                    asyncRespond(ctx, ipService.addIp(userId, teamId, ip, reason));
                }
                case "list" -> {
                    // Default: only active, limit 200
                    asyncRespond(ctx, ipService.listIps(true, 200));
                }
                default -> {
                    // Return usage message right away
                    ctx.respond("""
                            Usage:
                            • /ip add <IP> [reason]
                            • /ip list
                            """);
                }
            }
            // Return an empty ack since we've already acknowledged/responded
            return ctx.ack();
        });

        // Start Socket Mode. Use the *app-level* token (xapp-...)
        socketModeApp = new SocketModeApp(props.appToken(), app);
        socketModeApp.startAsync();

        SocketModeClient client = socketModeApp.getClient();
        log.info("Slack Socket Mode connected: {}", client != null);
    }

    private void asyncRespond(com.slack.api.bolt.context.builtin.SlashCommandContext ctx, Mono<String> mono) {
        mono.subscribe(
                msg -> {
                    try {
                        // respond() posts to response_url and returns WebhookResponse (NOT
                        // ChatPostMessageResponse)
                        WebhookResponse r = ctx.respond(msg);
                        if (r == null || r.getCode() >= 400) {
                            log.warn("Slack respond returned non-OK: code={} body={}",
                                    r == null ? -1 : r.getCode(),
                                    r == null ? "<null>" : r.getBody());
                        }
                    } catch (Exception e) {
                        log.error("Error sending Slack response", e);
                    }
                },
                err -> {
                    try {
                        ctx.respond(":x: Error: " + err.getMessage());
                    } catch (Exception e) {
                        log.error("Error sending failure to Slack", e);
                    }
                });
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
