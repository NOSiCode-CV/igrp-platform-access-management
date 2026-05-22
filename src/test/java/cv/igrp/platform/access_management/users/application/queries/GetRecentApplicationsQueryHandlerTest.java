package cv.igrp.platform.access_management.users.application.queries;

import cv.igrp.platform.access_management.app.mapper.ApplicationMapper;
import cv.igrp.platform.access_management.shared.application.dto.ApplicationDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.AccessHistoryEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.AccessHistoryEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetRecentApplicationsQueryHandlerTest {

    @Mock private AccessHistoryEntityRepository accessHistoryRepository;
    @Mock private IGRPUserEntityRepository userRepo;
    @Mock private ApplicationMapper applicationMapper;
    @Mock private AuthenticationHelper authenticationHelper;

    private GetRecentApplicationsQueryHandler handler;
    private final String userId = UUID.randomUUID().toString();
    private IGRPUserEntity user;

    @BeforeEach
    void setUp() {
        handler = new GetRecentApplicationsQueryHandler(accessHistoryRepository, userRepo, applicationMapper, authenticationHelper);
        user = new IGRPUserEntity();
        user.setId(userId);
        when(authenticationHelper.getSub()).thenReturn(userId);
    }

    private AccessHistoryEntity history(String code, String name, LocalDateTime lastAccess) {
        ApplicationEntity app = new ApplicationEntity();
        app.setCode(code);
        app.setName(name);
        AccessHistoryEntity h = new AccessHistoryEntity();
        h.setApplication(app);
        h.setLastAccess(lastAccess);
        return h;
    }

    @Test
    void handle_UserNotFound_Throws() {
        when(userRepo.findByIdWithRolesAndPermissions(userId)).thenReturn(Optional.empty());
        assertThrows(IgrpResponseStatusException.class, () -> handler.handle(new GetRecentApplicationsQuery()));
    }

    @Test
    void handle_ReturnsAll() {
        when(userRepo.findByIdWithRolesAndPermissions(userId)).thenReturn(Optional.of(user));
        AccessHistoryEntity h1 = history("A1", "Alpha", LocalDateTime.now());
        AccessHistoryEntity h2 = history("A2", "Beta", LocalDateTime.now().minusDays(1));
        when(accessHistoryRepository.findByUserIdOrderByLastAccessDesc(userId)).thenReturn(List.of(h1, h2));
        when(applicationMapper.toDto(any(ApplicationEntity.class))).thenAnswer(i -> {
            ApplicationDTO d = new ApplicationDTO();
            d.setCode(((ApplicationEntity) i.getArgument(0)).getCode());
            d.setName(((ApplicationEntity) i.getArgument(0)).getName());
            return d;
        });

        ResponseEntity<List<ApplicationDTO>> resp = handler.handle(new GetRecentApplicationsQuery());

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(2, resp.getBody().size());
    }

    @Test
    void handle_FiltersByMax() {
        when(userRepo.findByIdWithRolesAndPermissions(userId)).thenReturn(Optional.of(user));
        when(accessHistoryRepository.findByUserIdOrderByLastAccessDesc(userId)).thenReturn(List.of(
                history("A1", "Alpha", LocalDateTime.now()),
                history("A2", "Beta", LocalDateTime.now()),
                history("A3", "Gamma", LocalDateTime.now())));
        when(applicationMapper.toDto(any(ApplicationEntity.class))).thenAnswer(i -> {
            ApplicationDTO d = new ApplicationDTO();
            d.setCode(((ApplicationEntity) i.getArgument(0)).getCode());
            return d;
        });

        GetRecentApplicationsQuery q = new GetRecentApplicationsQuery();
        q.setMax(2);
        ResponseEntity<List<ApplicationDTO>> resp = handler.handle(q);

        assertEquals(2, resp.getBody().size());
    }

    @Test
    void handle_FiltersByCode() {
        when(userRepo.findByIdWithRolesAndPermissions(userId)).thenReturn(Optional.of(user));
        when(accessHistoryRepository.findByUserIdOrderByLastAccessDesc(userId)).thenReturn(List.of(
                history("A1", "Alpha", LocalDateTime.now()),
                history("A2", "Beta", LocalDateTime.now())));
        when(applicationMapper.toDto(any(ApplicationEntity.class))).thenAnswer(i -> {
            ApplicationDTO d = new ApplicationDTO();
            d.setCode(((ApplicationEntity) i.getArgument(0)).getCode());
            return d;
        });

        GetRecentApplicationsQuery q = new GetRecentApplicationsQuery();
        q.setApplicationCode("A2");
        ResponseEntity<List<ApplicationDTO>> resp = handler.handle(q);

        assertEquals(1, resp.getBody().size());
        assertEquals("A2", resp.getBody().get(0).getCode());
    }

    @Test
    void handle_FiltersByName() {
        when(userRepo.findByIdWithRolesAndPermissions(userId)).thenReturn(Optional.of(user));
        when(accessHistoryRepository.findByUserIdOrderByLastAccessDesc(userId)).thenReturn(List.of(
                history("A1", "Alpha", LocalDateTime.now()),
                history("A2", "Beta", LocalDateTime.now())));
        when(applicationMapper.toDto(any(ApplicationEntity.class))).thenAnswer(i -> {
            ApplicationDTO d = new ApplicationDTO();
            d.setName(((ApplicationEntity) i.getArgument(0)).getName());
            return d;
        });

        GetRecentApplicationsQuery q = new GetRecentApplicationsQuery();
        q.setApplicationName("alp");
        ResponseEntity<List<ApplicationDTO>> resp = handler.handle(q);

        assertEquals(1, resp.getBody().size());
    }
}
