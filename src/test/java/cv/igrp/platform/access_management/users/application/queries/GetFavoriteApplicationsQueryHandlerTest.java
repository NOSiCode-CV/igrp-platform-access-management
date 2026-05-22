package cv.igrp.platform.access_management.users.application.queries;

import cv.igrp.platform.access_management.app.mapper.ApplicationMapper;
import cv.igrp.platform.access_management.shared.application.dto.ApplicationDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.FavoriteApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.FavoriteApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetFavoriteApplicationsQueryHandlerTest {

    @Mock private FavoriteApplicationEntityRepository favoriteRepo;
    @Mock private IGRPUserEntityRepository userRepo;
    @Mock private ApplicationMapper applicationMapper;
    @Mock private AuthenticationHelper authenticationHelper;

    private GetFavoriteApplicationsQueryHandler handler;
    private final String userId = UUID.randomUUID().toString();
    private IGRPUserEntity user;

    @BeforeEach
    void setUp() {
        handler = new GetFavoriteApplicationsQueryHandler(favoriteRepo, userRepo, applicationMapper, authenticationHelper);
        user = new IGRPUserEntity();
        user.setId(userId);
        when(authenticationHelper.getSub()).thenReturn(userId);
    }

    @Test
    void handle_UserNotFound_Throws() {
        when(userRepo.findByIdWithRolesAndPermissions(userId)).thenReturn(Optional.empty());
        assertThrows(IgrpResponseStatusException.class, () -> handler.handle(new GetFavoriteApplicationsQuery()));
    }

    @Test
    void handle_NoFavorites_ReturnsEmpty() {
        when(userRepo.findByIdWithRolesAndPermissions(userId)).thenReturn(Optional.of(user));
        when(favoriteRepo.findByUserId(userId)).thenReturn(Optional.empty());

        ResponseEntity<List<ApplicationDTO>> resp = handler.handle(new GetFavoriteApplicationsQuery());

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertTrue(resp.getBody().isEmpty());
    }

    @Test
    void handle_ReturnsAllFavorites() {
        ApplicationEntity a1 = new ApplicationEntity();
        a1.setCode("A1"); a1.setName("Alpha");
        ApplicationEntity a2 = new ApplicationEntity();
        a2.setCode("A2"); a2.setName("Beta");
        FavoriteApplicationEntity fav = new FavoriteApplicationEntity();
        fav.setApplications(new HashSet<>(Set.of(a1, a2)));

        when(userRepo.findByIdWithRolesAndPermissions(userId)).thenReturn(Optional.of(user));
        when(favoriteRepo.findByUserId(userId)).thenReturn(Optional.of(fav));
        when(applicationMapper.toDto(any(ApplicationEntity.class))).thenAnswer(i -> {
            ApplicationDTO d = new ApplicationDTO();
            d.setCode(((ApplicationEntity) i.getArgument(0)).getCode());
            return d;
        });

        ResponseEntity<List<ApplicationDTO>> resp = handler.handle(new GetFavoriteApplicationsQuery());

        assertEquals(2, resp.getBody().size());
    }

    @Test
    void handle_FiltersByName() {
        ApplicationEntity a1 = new ApplicationEntity();
        a1.setCode("A1"); a1.setName("Alpha");
        ApplicationEntity a2 = new ApplicationEntity();
        a2.setCode("A2"); a2.setName("Beta");
        FavoriteApplicationEntity fav = new FavoriteApplicationEntity();
        fav.setApplications(new HashSet<>(Set.of(a1, a2)));

        when(userRepo.findByIdWithRolesAndPermissions(userId)).thenReturn(Optional.of(user));
        when(favoriteRepo.findByUserId(userId)).thenReturn(Optional.of(fav));
        when(applicationMapper.toDto(any(ApplicationEntity.class))).thenAnswer(i -> {
            ApplicationDTO d = new ApplicationDTO();
            d.setName(((ApplicationEntity) i.getArgument(0)).getName());
            return d;
        });

        GetFavoriteApplicationsQuery q = new GetFavoriteApplicationsQuery();
        q.setApplicationName("alp");
        ResponseEntity<List<ApplicationDTO>> resp = handler.handle(q);

        assertEquals(1, resp.getBody().size());
        assertEquals("Alpha", resp.getBody().get(0).getName());
    }

    @Test
    void handle_FiltersByCode() {
        ApplicationEntity a1 = new ApplicationEntity();
        a1.setCode("A1"); a1.setName("Alpha");
        ApplicationEntity a2 = new ApplicationEntity();
        a2.setCode("B2"); a2.setName("Beta");
        FavoriteApplicationEntity fav = new FavoriteApplicationEntity();
        fav.setApplications(new HashSet<>(Set.of(a1, a2)));

        when(userRepo.findByIdWithRolesAndPermissions(userId)).thenReturn(Optional.of(user));
        when(favoriteRepo.findByUserId(userId)).thenReturn(Optional.of(fav));
        when(applicationMapper.toDto(any(ApplicationEntity.class))).thenAnswer(i -> {
            ApplicationDTO d = new ApplicationDTO();
            d.setCode(((ApplicationEntity) i.getArgument(0)).getCode());
            return d;
        });

        GetFavoriteApplicationsQuery q = new GetFavoriteApplicationsQuery();
        q.setApplicationCode("b2");
        ResponseEntity<List<ApplicationDTO>> resp = handler.handle(q);

        assertEquals(1, resp.getBody().size());
        assertEquals("B2", resp.getBody().get(0).getCode());
    }
}
