package com.blacklisthub.slack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.blacklisthub.entity.IpEntity;
import com.blacklisthub.entity.SlackUserEntity;
import com.blacklisthub.repository.IpRepository;
import com.blacklisthub.slack.util.AuditHelper;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class IpCommandServiceTest {

    /**
     * T-05: an internal failure (e.g. a raw DB/SQL error) must never reach the
     * user-facing Slack message; only a generic message is returned.
     */
    @Test
    void addIpDoesNotLeakInternalErrorDetailsToUser() {
        IpRepository ipRepository = mock(IpRepository.class);
        AuditHelper auditHelper = mock(AuditHelper.class);
        SlackUserService slackUserService = mock(SlackUserService.class);
        IpCommandService service = new IpCommandService(ipRepository, auditHelper, slackUserService);

        SlackUserEntity user = SlackUserEntity.builder().id(1L).build();
        when(slackUserService.ensureAndEnrichSlackUser(anyString(), anyString())).thenReturn(Mono.just(user));
        // The lookup fails with a raw error that carries sensitive internals.
        when(ipRepository.findByIpNormalized("203.0.113.5"))
                .thenReturn(Mono.error(new RuntimeException(
                        "Table 'blacklist_hub.ip_addresses' - SQL syntax error near INET6_ATON")));
        // switchIfEmpty builds its fallback eagerly; give save a non-null Mono so the
        // fallback assembly does not throw and the lookup error is what propagates.
        when(ipRepository.save(any(IpEntity.class))).thenReturn(Mono.just(new IpEntity()));

        StepVerifier.create(service.addIp("U123", "T123", "203.0.113.5", "reason"))
                .assertNext(msg -> {
                    // No internal details leaked.
                    assertThat(msg).doesNotContain("SQL");
                    assertThat(msg).doesNotContain("ip_addresses");
                    assertThat(msg).doesNotContain("blacklist_hub");
                    assertThat(msg).doesNotContain("INET6_ATON");
                    // Still actionable: operation + the user's own input value.
                    assertThat(msg).contains("Error while adding");
                    assertThat(msg).contains("203.0.113.5");
                })
                .verifyComplete();
    }
}
