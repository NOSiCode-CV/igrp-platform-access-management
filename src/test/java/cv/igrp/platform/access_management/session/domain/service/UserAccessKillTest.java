package cv.igrp.platform.access_management.session.domain.service;

import cv.igrp.platform.access_management.oauth_server.infrastructure.security.UserAuthorizations;
import cv.igrp.platform.access_management.shared.domain.events.UserAccessEndedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** v3-11 (#19): a kill ends sessions, authorizations and keys for each user, once. */
@ExtendWith(MockitoExtension.class)
class UserAccessKillTest {

    @Mock SessionInvalidationService sessions;
    @Mock UserAuthorizations authorizations;
    @Mock ApplicationEventPublisher events;
    @InjectMocks UserAccessKill kill;

    @Test
    void killsEachUserOnceAndRecordsWhoAsked() {
        int killed = kill.kill(List.of("u1", "u2", "u1"), "ADMIN_KILL", "maria");

        assertThat(killed).isEqualTo(2);
        verify(sessions).invalidateUserSessions(Set.of("u1", "u2"), "ADMIN_KILL", "maria");
        verify(authorizations).removeAll("u1");
        verify(authorizations).removeAll("u2");
        verify(events).publishEvent(new UserAccessEndedEvent(Set.of("u1", "u2"), "ADMIN_KILL"));
    }

    @Test
    void nobodyToKillDoesNothing() {
        List<String> nobody = new ArrayList<>();
        nobody.add(null);

        assertThat(kill.kill(nobody, "ADMIN_KILL", "maria")).isZero();
        verifyNoInteractions(sessions, authorizations, events);
    }
}
