package com.blacklisthub.service;

import org.springframework.stereotype.Service;

import com.blacklisthub.entity.IpEntity;
import com.blacklisthub.repository.IpRepository;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class IpService {
    private final IpRepository ipRepository;

    public Flux<String> findActiveIps() {
        return ipRepository.findByActiveTrueOrderByIpAsc()
                .map(IpEntity::getIp);
    }
}