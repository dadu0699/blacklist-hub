package com.blacklisthub.slack;

import org.springframework.stereotype.Component;

import com.blacklisthub.slack.config.SlackProps;
import com.blacklisthub.slack.service.ChannelAccessService;
import com.blacklisthub.slack.service.DomainCommandService;
import com.blacklisthub.slack.service.HashCommandService;
import com.blacklisthub.slack.service.IpCommandService;
import com.blacklisthub.slack.service.UrlCommandService;
import com.blacklisthub.slack.util.CommandParser;
import com.blacklisthub.slack.util.SlackMessageFormatter;
import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;
import com.slack.api.bolt.socket_mode.SocketModeApp;
import com.slack.api.model.event.AppMentionEvent;

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
    private final ChannelAccessService channelAccessService;

    private final IpCommandService ipCommandService;
    private final HashCommandService hashCommandService;
    private final DomainCommandService domainCommandService;
    private final UrlCommandService urlCommandService;

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
            final String commandName = req.getPayload().getCommand();

            log.info("Received {} '{}' from user={} in channel={}", commandName, text, userId, channelId);
            ctx.ack(":hourglass_flowing_sand: processing…");

            // Use the generic helper, passing the command name
            executeCommand(ipCommandService.execute(CommandParser.parse(text), userId, teamId, channelId),
                    commandName, channelId, userId, text) // <-- Pass commandName
                    .subscribe(
                            response -> {
                                try {
                                    ctx.respond(r -> r.responseType("in_channel").text(response));
                                } catch (Exception e) {
                                    log.error("Failed to respond to /ip", e);
                                }
                            },
                            err -> {
                                try {
                                    ctx.respond(r -> r.responseType("ephemeral")
                                            .text(":x: Internal error: " + err.getMessage()));
                                } catch (Exception e) {
                                    log.error("Failed to respond error to /ip", e);
                                }
                            });
            return ctx.ack();
        });

        app.command("/hash", (req, ctx) -> {
            final String text = req.getPayload().getText();
            final String channelId = req.getPayload().getChannelId();
            final String userId = req.getPayload().getUserId();
            final String teamId = req.getPayload().getTeamId();
            final String commandName = req.getPayload().getCommand(); // <-- Get command name

            log.info("Received {} '{}' from user={} in channel={}", commandName, text, userId, channelId);
            ctx.ack(":hourglass_flowing_sand: processing…");

            executeCommand(hashCommandService.execute(CommandParser.parse(text), userId, teamId, channelId),
                    commandName, channelId, userId, text) // <-- Pass commandName
                    .subscribe(
                            response -> {
                                try {
                                    ctx.respond(r -> r.responseType("in_channel").text(response));
                                } catch (Exception e) {
                                    log.error("Failed to respond to /hash", e);
                                }
                            },
                            err -> {
                                try {
                                    ctx.respond(r -> r.responseType("ephemeral")
                                            .text(":x: Internal error: " + err.getMessage()));
                                } catch (Exception e) {
                                    log.error("Failed to respond error to /hash", e);
                                }
                            });
            return ctx.ack();
        });

        app.command("/domain", (req, ctx) -> {
            final String text = req.getPayload().getText();
            final String channelId = req.getPayload().getChannelId();
            final String userId = req.getPayload().getUserId();
            final String teamId = req.getPayload().getTeamId();
            final String commandName = req.getPayload().getCommand(); // <-- Get command name

            log.info("Received {} '{}' from user={} in channel={}", commandName, text, userId, channelId);
            ctx.ack(":hourglass_flowing_sand: processing…");

            executeCommand(domainCommandService.execute(CommandParser.parse(text), userId, teamId, channelId),
                    commandName, channelId, userId, text) // <-- Pass commandName
                    .subscribe(
                            response -> {
                                try {
                                    ctx.respond(r -> r.responseType("in_channel").text(response));
                                } catch (Exception e) {
                                    log.error("Failed to respond to /domain", e);
                                }
                            },
                            err -> {
                                try {
                                    ctx.respond(r -> r.responseType("ephemeral")
                                            .text(":x: Internal error: " + err.getMessage()));
                                } catch (Exception e) {
                                    log.error("Failed to respond error to /domain", e);
                                }
                            });
            return ctx.ack();
        });

        app.command("/url", (req, ctx) -> {
            final String text = req.getPayload().getText();
            final String channelId = req.getPayload().getChannelId();
            final String userId = req.getPayload().getUserId();
            final String teamId = req.getPayload().getTeamId();
            final String commandName = req.getPayload().getCommand(); // <-- Get command name

            log.info("Received {} '{}' from user={} in channel={}", commandName, text, userId, channelId);
            ctx.ack(":hourglass_flowing_sand: processing…");

            executeCommand(urlCommandService.execute(CommandParser.parse(text), userId, teamId, channelId),
                    commandName, channelId, userId, text) // <-- Pass commandName
                    .subscribe(
                            response -> {
                                try {
                                    ctx.respond(r -> r.responseType("in_channel").text(response));
                                } catch (Exception e) {
                                    log.error("Failed to respond to /url", e);
                                }
                            },
                            err -> {
                                try {
                                    ctx.respond(r -> r.responseType("ephemeral")
                                            .text(":x: Internal error: " + err.getMessage()));
                                } catch (Exception e) {
                                    log.error("Failed to respond error to /url", e);
                                }
                            });
            return ctx.ack();
        });

        // Optional: respond to @mentions for a quick health check
        app.event(AppMentionEvent.class, (payload, ctx) -> {
            String channelId = payload.getEvent().getChannel();
            log.info("App mentioned in channel {}", channelId);
            ctx.say("👋 I'm alive and managing IoC blocklist commands (/ip, /hash, /domain, /url).");
            return ctx.ack();
        });

        // Start Socket Mode using the app-level token (xapp-***)
        socketModeApp = new SocketModeApp(props.appToken(), app);
        socketModeApp.startAsync();

        log.info("✅ Slack Bolt runner started successfully in Socket Mode.");
    }

    /**
     * Generic helper to wrap command execution with channel validation and response
     * formatting.
     * NOW accepts commandName.
     */
    private Mono<String> executeCommand(Mono<String> serviceExecution, String commandName, String channelId,
            String userId, String commandText) {
        return channelAccessService.isChannelAllowed(channelId)
                .flatMap(allowed -> {
                    if (!Boolean.TRUE.equals(allowed)) {
                        log.warn("Command from user {} in unauthorized channel {}", userId, channelId);
                        return Mono.just(":no_entry_sign: Commands are not allowed in this channel.");
                    }

                    var parsed = CommandParser.parse(commandText);

                    return serviceExecution.flatMap(resultMessage -> {
                        // Return the formatted result for the "in_channel" response
                        String publicMsg = SlackMessageFormatter.prettyResultForChannel(userId, resultMessage, parsed);
                        return Mono.just(publicMsg);
                    });
                })
                .onErrorResume(err -> {
                    log.error("Error executing command for user {}: {}", userId, err.getMessage(), err);
                    return Mono.just(":x: Internal error: " + err.getMessage());
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