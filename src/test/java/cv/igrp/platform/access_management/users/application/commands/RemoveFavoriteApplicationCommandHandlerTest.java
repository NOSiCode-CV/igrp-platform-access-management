package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.FavoriteApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
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

import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RemoveFavoriteApplicationCommandHandlerTest {

    @Mock private AuthenticationHelper authenticationHelper;
    @Mock private FavoriteApplicationEntityRepository favoriteApplicationEntityRepository;
    @Mock private ApplicationEntityRepository applicationEntityRepository;
    @Mock private IGRPUserEntityRepository userEntityRepository;

    private RemoveFavoriteApplicationCommandHandler handler;

    private final String userId = UUID.randomUUID().toString();
    private IGRPUserEntity user;
    private ApplicationEntity app;
    private RemoveFavoriteApplicationCommand command;

    @BeforeEach
    void setUp() {
        handler = new RemoveFavoriteApplicationCommandHandler(
                authenticationHelper, favoriteApplicationEntityRepository,
                applicationEntityRepository, userEntityRepository);
        user = new IGRPUserEntity();
        user.setId(userId);
        app = new ApplicationEntity();
        app.setCode("APP1");
        command = new RemoveFavoriteApplicationCommand();
        command.setApplicationCode("APP1");
        when(authenticationHelper.getSub()).thenReturn(userId);
    }

    @Test
    void handle_UserNotFound_Throws() {
        when(userEntityRepository.findByIdWithRolesAndPermissions(userId)).thenReturn(Optional.empty());
        assertThrows(IgrpResponseStatusException.class, () -> handler.handle(command));
    }

    @Test
    void handle_NotFavorited_Throws() {
        when(userEntityRepository.findByIdWithRolesAndPermissions(userId)).thenReturn(Optional.of(user));
        when(applicationEntityRepository.findByCodeAndStatusNotDeleted("APP1")).thenReturn(app);
        when(favoriteApplicationEntityRepository.existsByUserAndApplication(userId, app)).thenReturn(false);

        assertThrows(IgrpResponseStatusException.class, () -> handler.handle(command));
    }

    @Test
    void handle_NoFavoriteRecord_Throws() {
        when(userEntityRepository.findByIdWithRolesAndPermissions(userId)).thenReturn(Optional.of(user));
        when(applicationEntityRepository.findByCodeAndStatusNotDeleted("APP1")).thenReturn(app);
        when(favoriteApplicationEntityRepository.existsByUserAndApplication(userId, app)).thenReturn(true);
        when(favoriteApplicationEntityRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThrows(IgrpResponseStatusException.class, () -> handler.handle(command));
    }

    @Test
    void handle_RemovesFavorite() {
        FavoriteApplicationEntity fav = new FavoriteApplicationEntity();
        fav.setUserId(userId);
        fav.setApplications(new HashSet<>());
        fav.getApplications().add(app);

        when(userEntityRepository.findByIdWithRolesAndPermissions(userId)).thenReturn(Optional.of(user));
        when(applicationEntityRepository.findByCodeAndStatusNotDeleted("APP1")).thenReturn(app);
        when(favoriteApplicationEntityRepository.existsByUserAndApplication(userId, app)).thenReturn(true);
        when(favoriteApplicationEntityRepository.findByUserId(userId)).thenReturn(Optional.of(fav));
        when(favoriteApplicationEntityRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ResponseEntity<String> resp = handler.handle(command);

        assertEquals(HttpStatus.NO_CONTENT, resp.getStatusCode());
        assertFalse(fav.getApplications().contains(app));
        verify(favoriteApplicationEntityRepository).save(fav);
    }
}
