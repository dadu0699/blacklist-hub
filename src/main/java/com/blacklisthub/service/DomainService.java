package com.blacklisthub.service;

import org.springframework.stereotype.Service;

import com.blacklisthub.entity.DomainEntity;
import com.blacklisthub.repository.DomainRepository;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class DomainService {
    private final DomainRepository domainRepository;

    public Flux<String> findActiveDomains() {
        return domainRepository.findByActiveTrueOrderByDomainNameAsc()
                .map(DomainEntity::getDomainName);
    }
}
