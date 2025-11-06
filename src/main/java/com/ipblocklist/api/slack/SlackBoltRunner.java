package com.ipblocklist.api.slack;

import org.springframework.stereotype.Component;

import com.ipblocklist.api.slack.IpCommandParser.Parsed;
import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;
import com.slack.api.bolt.socket_mode.SocketModeApp;
import com.slack.api.socket_mode.SocketModeClient;
import com.slack.api.webhook.WebhookResponse;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
        // Build a proper AppConfig for Bolt
        AppConfig config = AppConfig.builder()
                .singleTeamBotToken(props.botToken())
                // signingSecret is optional if you only use Socket Mode (no public Events API)
                .signingSecret(props.signingSecret() == null ? "" : props.signingSecret())
                .build();

        App app = new App(config);

        // /ip command
        app.command("/ip", (req, ctx) -> {
            final String userId = req.getPayload().getUserId();
            final String teamId = req.getPayload().getTeamId();
            final String text = req.getPayload().getText();

            // Ack quickly to avoid Slack timeout
            ctx.ack(":hourglass_flowing_sand: processing…");

            Parsed p = IpCommandParser.parse(text);
            switch (p.sub()) {
                case "add" -> {
                    final String ip = p.args().isEmpty() ? "" : p.args().get(0);
                    final String reason = p.tail().isBlank() ? null : p.tail();
                    asyncRespond(ctx, ipService.addIp(userId, teamId, ip, reason));
                }
                case "deactivate" -> {
                    final String ip = p.args().isEmpty() ? "" : p.args().get(0);
                    final String reason = p.tail().isBlank() ? null : p.tail();
                    asyncRespond(ctx, ipService.deactivateIp(userId, teamId, ip, reason));
                }
                case "reactivate" -> {
                    final String ip = p.args().isEmpty() ? "" : p.args().get(0);
                    final String reason = p.tail().isBlank() ? null : p.tail();
                    asyncRespond(ctx, ipService.reactivateIp(userId, teamId, ip, reason));
                }
                case "edit" -> {
                    final String ip = p.args().isEmpty() ? "" : p.args().get(0);
                    final String newReason = p.tail().isBlank() ? null : p.tail();
                    asyncRespond(ctx, ipService.editIp(userId, teamId, ip, newReason));
                }
                case "list" -> {
                    // Default: only active, limit 200
                    asyncRespond(ctx, ipService.listIps(true, 200));
                }
                case "" -> {
                    // No subcommand provided
                    ctx.respond("""
                            Usage:
                            • /ip add <IP> [reason]
                            • /ip deactivate <IP> [reason]
                            • /ip reactivate <IP> [reason]
                            • /ip edit <IP> <new reason>
                            • /ip list
                            """);
                }
                default -> {
                    ctx.respond(":warning: Unknown subcommand: `" + p.sub() + "`\n" +
                            "Try `/ip list` or see `/ip` usage.");
                }
            }

            // Return an empty ack since we've already acknowledged/responded
            return ctx.ack();
        });

        // Start Socket Mode with xapp token
        socketModeApp = new SocketModeApp(props.appToken(), app);
        socketModeApp.startAsync();

        SocketModeClient client = socketModeApp.getClient();
        log.info("Slack Socket Mode connected: {}", client != null);
    }

    private void asyncRespond(com.slack.api.bolt.context.builtin.SlashCommandContext ctx, Mono<String> mono) {
        mono.subscribe(
                msg -> {
                    try {
                        // respond() posts via response_url and returns WebhookResponse
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
