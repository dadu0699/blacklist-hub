package com.ipblocklist.api.slack.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.ipblocklist.api.repository.SlackChannelWhitelistRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelAccessService {

    private final SlackChannelWhitelistRepository repo;

    @Value("#{'${app.allowed-channels:}'.empty ? T(java.util.Collections).emptyList() : '${app.allowed-channels}'.split(',')}")
    private List<String> staticWhitelist;

    /**
     * Checks whether a given Slack channel is authorized to execute commands.
     * Combines DB whitelist and optional static list from application.yml.
     */
    public Mono<Boolean> isChannelAllowed(String channelId) {
        log.debug("Checking channel access for {}", channelId);

        return repo.existsByChannelIdAndActiveTrue(channelId)
                .defaultIfEmpty(false)
                .map(dbAllowed -> {
                    boolean allowed = dbAllowed || staticWhitelist.contains(channelId);
                    log.info("Channel {} -> dbAllowed={} staticAllowed={} final={}",
                            channelId, dbAllowed, staticWhitelist.contains(channelId), allowed);
                    return allowed;
                });
    }
}
