package com.blacklisthub.service;

import org.springframework.stereotype.Service;

import com.blacklisthub.entity.HashEntity;
import com.blacklisthub.repository.HashRepository;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class HashService {
    private final HashRepository hashRepository;

    public Flux<String> findActiveHashes() {
        return hashRepository.findByActiveTrueOrderByHashValueAsc()
                .map(HashEntity::getHashValue);
    }
}