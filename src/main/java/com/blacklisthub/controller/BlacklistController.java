package com.blacklisthub.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.blacklisthub.service.DomainService;
import com.blacklisthub.service.HashService;
import com.blacklisthub.service.IpService;
import com.blacklisthub.service.UrlService;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/blacklist")
@RequiredArgsConstructor
public class BlacklistController {

    private final IpService ipService;
    private final HashService hashService;
    private final DomainService domainService;
    private final UrlService urlService;

    @GetMapping(value = "/ips.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public Mono<String> getIpBlocklist() {
        return ipService.findActiveIps()
                .collectList()
                .map(list -> String.join("\n", list) + "\n");
    }

    @GetMapping(value = "/hashes.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public Mono<String> getHashBlocklist() {
        return hashService.findActiveHashes()
                .collectList()
                .map(list -> String.join("\n", list) + "\n");
    }

    @GetMapping(value = "/domains.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public Mono<String> getDomainBlocklist() {
        return domainService.findActiveDomains()
                .collectList()
                .map(list -> String.join("\n", list) + "\n");
    }

    @GetMapping(value = "/urls.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public Mono<String> getUrlBlocklist() {
        return urlService.findActiveUrls()
                .collectList()
                .map(list -> String.join("\n", list) + "\n");
    }
}