package cv.igrp.platform.access_management.session.domain.service;

import cv.igrp.platform.access_management.oauth_server.infrastructure.security.UserAuthorizations;
import cv.igrp.platform.access_management.security_audit.application.service.AccessAuditLogger;
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

/** v3-11 (#19), #33: a kill ends authorizations and keys for each user, once, and is audited. */
@ExtendWith(MockitoExtension.class)
class UserAccessKillTest {

    @Mock UserAuthorizations authorizations;
    @Mock ApplicationEventPublisher events;
    @Mock AccessAuditLogger audit;
    @InjectMocks UserAccessKill kill;

    @Test
    void killsEachUserOnceAndRecordsWhoAsked() {
        int killed = kill.kill(List.of("u1", "u2", "u1"), "ADMIN_KILL", "maria");

        assertThat(killed).isEqualTo(2);
        verify(authorizations).removeAll("u1");
        verify(authorizations).removeAll("u2");
        verify(audit).recordAccessEnded("u1", "ADMIN_KILL", "maria");
        verify(audit).recordAccessEnded("u2", "ADMIN_KILL", "maria");
        verify(events).publishEvent(new UserAccessEndedEvent(Set.of("u1", "u2"), "ADMIN_KILL"));
    }

    @Test
    void nobodyToKillDoesNothing() {
        List<String> nobody = new ArrayList<>();
        nobody.add(null);

        assertThat(kill.kill(nobody, "ADMIN_KILL", "maria")).isZero();
        verifyNoInteractions(authorizations, events, audit);
    }
}
