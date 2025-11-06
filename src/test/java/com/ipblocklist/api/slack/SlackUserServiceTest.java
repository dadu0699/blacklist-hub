package com.ipblocklist.api.slack;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.ipblocklist.api.entity.SlackUserEntity;
import com.ipblocklist.api.repository.SlackUserRepository;
import com.ipblocklist.api.slack.service.SlackUserService;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.request.users.UsersInfoRequest;
import com.slack.api.methods.response.users.UsersInfoResponse;
import com.slack.api.model.User;
import com.slack.api.model.User.Profile;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class SlackUserServiceTest {

    private SlackUserRepository userRepo;
    private MethodsClient methodsClient;
    private SlackUserService service;

    @BeforeEach
    void setup() {
        userRepo = Mockito.mock(SlackUserRepository.class);
        methodsClient = Mockito.mock(MethodsClient.class);
        service = new SlackUserService(userRepo, methodsClient);
    }

    @Test
    @SuppressWarnings("null")
    void testEnsureUserCreatesNew() throws Exception {
        SlackUserEntity created = SlackUserEntity.builder()
                .slackUserId("U123")
                .displayName("U123")
                .teamId("T001")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Profile profile = new Profile();
        profile.setDisplayNameNormalized("JohnD");
        profile.setRealNameNormalized("John Doe");
        User slackUser = new User();
        slackUser.setProfile(profile);
        UsersInfoResponse resp = new UsersInfoResponse();
        resp.setOk(true);
        resp.setUser(slackUser);

        when(userRepo.findBySlackUserId("U123")).thenReturn(Mono.empty());
        when(userRepo.save(any(SlackUserEntity.class))).thenReturn(Mono.just(created));
        when(methodsClient.usersInfo(any(UsersInfoRequest.class))).thenReturn(resp);
        when(userRepo.save(any(SlackUserEntity.class))).thenReturn(Mono.just(created));

        StepVerifier.create(service.ensureAndEnrichSlackUser("U123", "T001"))
                .expectNextMatches(u -> u.getDisplayName().equals("JohnD"))
                .verifyComplete();
    }
}
