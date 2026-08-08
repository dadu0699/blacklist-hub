package com.blacklisthub.slack.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.blacklisthub.entity.SlackUserEntity;
import com.blacklisthub.repository.SlackUserRepository;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.request.users.UsersInfoRequest;
import com.slack.api.methods.response.users.UsersInfoResponse;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class SlackUserServiceTest {

    /**
     * T-12: a second lookup of the same user within the cache TTL must not hit
     * Slack's users.info nor the database again.
     */
    @Test
    void secondLookupIsServedFromCacheWithoutHittingSlackOrDb() throws Exception {
        SlackUserRepository slackUserRepository = mock(SlackUserRepository.class);
        MethodsClient slackMethods = mock(MethodsClient.class);
        SlackUserService service = new SlackUserService(slackUserRepository, slackMethods);

        SlackUserEntity existing = SlackUserEntity.builder()
                .id(1L)
                .slackUserId("U1")
                .displayName("existing")
                .build();
        when(slackUserRepository.findBySlackUserId("U1")).thenReturn(Mono.just(existing));

        // Not-ok response so enrichment short-circuits and returns the stored user as-is.
        UsersInfoResponse notOk = new UsersInfoResponse();
        notOk.setOk(false);
        when(slackMethods.usersInfo(any(UsersInfoRequest.class))).thenReturn(notOk);

        StepVerifier.create(service.ensureAndEnrichSlackUser("U1", "T1"))
                .expectNext(existing)
                .verifyComplete();

        StepVerifier.create(service.ensureAndEnrichSlackUser("U1", "T1"))
                .expectNext(existing)
                .verifyComplete();

        // Both the external call and the DB read happened only once.
        verify(slackMethods, times(1)).usersInfo(any(UsersInfoRequest.class));
        verify(slackUserRepository, times(1)).findBySlackUserId("U1");
        // Existing user found: the create branch (save) must never run (lazy switchIfEmpty).
        verify(slackUserRepository, never()).save(any());
    }
}
