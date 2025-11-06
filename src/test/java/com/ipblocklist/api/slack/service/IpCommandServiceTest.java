package com.ipblocklist.api.slack.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.ipblocklist.api.entity.IpEntity;
import com.ipblocklist.api.entity.SlackUserEntity;
import com.ipblocklist.api.repository.IpRepository;
import com.ipblocklist.api.slack.util.AuditHelper;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class IpCommandServiceTest {

    private IpRepository ipRepo;
    private AuditHelper auditHelper;
    private SlackUserService slackUserService;
    private IpCommandService service;

    @BeforeEach
    void setup() {
        ipRepo = Mockito.mock(IpRepository.class);
        auditHelper = Mockito.mock(AuditHelper.class);
        slackUserService = Mockito.mock(SlackUserService.class);

        service = new IpCommandService(ipRepo, auditHelper, slackUserService);
    }

    @Test
    @SuppressWarnings("null")
    void testAddIpCreatesNew() {
        SlackUserEntity user = SlackUserEntity.builder()
                .id(1L)
                .slackUserId("U123")
                .displayName("testuser")
                .build();

        IpEntity newIp = IpEntity.builder()
                .id(1L)
                .ip("1.1.1.1")
                .reason("test")
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        when(slackUserService.ensureAndEnrichSlackUser(any(), any())).thenReturn(Mono.just(user));
        when(ipRepo.findByIp("1.1.1.1")).thenReturn(Mono.empty());
        when(ipRepo.save(any())).thenReturn(Mono.just(newIp));
        when(auditHelper.log(any(), any(), any(), any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(service.addIp("U123", "T001", "1.1.1.1", "test"))
                .expectNext(":white_check_mark: Added `1.1.1.1`")
                .verifyComplete();
    }

    @Test
    void testInvalidIpReturnsWarning() {
        StepVerifier.create(service.addIp("U123", "T001", "bad-ip", "test"))
                .expectNext(":warning: Invalid IP: `bad-ip`")
                .verifyComplete();
    }
}
