package com.blacklisthub.service;

import org.springframework.stereotype.Service;

import com.blacklisthub.entity.UrlEntity;
import com.blacklisthub.repository.UrlRepository;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class UrlService {
    private final UrlRepository urlRepository;

    public Flux<String> findActiveUrls() {
        return urlRepository.findByActiveTrueOrderByUrlValueAsc()
                .map(UrlEntity::getUrlValue);
    }
}