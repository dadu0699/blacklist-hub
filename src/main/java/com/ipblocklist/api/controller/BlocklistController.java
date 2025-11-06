package com.ipblocklist.api.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ipblocklist.api.service.IpService;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class BlocklistController {

    private final IpService ipService;

    @GetMapping(value = "/blocklist.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public Mono<String> getBlocklist() {
        return ipService.findActiveIps()
                .collectList()
                .map(list -> String.join("\n", list) + "\n");
    }
}
