package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.users.mapper.IGRPUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * BE-10 (FR-10): no writer of {@code IGRPUserEntity.metadata} other than
 * {@code PUT /users/me/locale} may drop the stored {@code metadata.locale}.
 */
class MetadataLocalePreservationTest {

    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";

    private final IGRPUserEntityRepository userRepository = mock(IGRPUserEntityRepository.class);
    private IGRPUserEntity user;

    @BeforeEach
    void setUp() {
        user = new IGRPUserEntity();
        user.setId(USER_ID);
        user.setName("Old");
        user.setMetadata(new LinkedHashMap<>(Map.of("locale", "fr", "given_name", "John")));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.save(any(IGRPUserEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void updateUser_keepsMetadataLocale_evenWhenTheDtoCarriesOtherMetadata() {
        UpdateUserCommandHandler handler = new UpdateUserCommandHandler(userRepository, mock(IGRPUserMapper.class));
        IGRPUserDTO dto = new IGRPUserDTO();
        dto.setName("New");
        dto.setEmail("new@example.com");
        dto.setMetadata(new LinkedHashMap<>(Map.of("other", "x"))); // no "locale" key

        handler.handle(new UpdateUserCommand(dto, USER_ID));

        assertThat(user.getName()).isEqualTo("New");
        assertThat(user.getMetadata()).containsEntry("locale", "fr").containsEntry("given_name", "John");
    }

    @Test
    void updateUser_withEmptyDtoMetadata_keepsMetadataLocale() {
        UpdateUserCommandHandler handler = new UpdateUserCommandHandler(userRepository, mock(IGRPUserMapper.class));
        IGRPUserDTO dto = new IGRPUserDTO();
        dto.setMetadata(new LinkedHashMap<>());

        handler.handle(new UpdateUserCommand(dto, USER_ID));

        assertThat(user.getMetadata()).containsEntry("locale", "fr");
    }

    @Test
    void replaceMetadata_withoutLocale_keepsTheStoredLocale() {
        UpdateUserMetadataCommandHandler handler = new UpdateUserMetadataCommandHandler(userRepository);

        handler.handle(new UpdateUserMetadataCommand(USER_ID, Map.of("billing.plan", "gold")));

        assertThat(user.getMetadata())
                .containsEntry("locale", "fr")
                .containsEntry("billing.plan", "gold")
                .doesNotContainKey("given_name"); // replace semantics otherwise unchanged
    }

    @Test
    void replaceMetadata_withExplicitLocale_usesTheNewValue() {
        UpdateUserMetadataCommandHandler handler = new UpdateUserMetadataCommandHandler(userRepository);

        handler.handle(new UpdateUserMetadataCommand(USER_ID, Map.of("locale", "en")));

        assertThat(user.getMetadata()).containsEntry("locale", "en");
    }
}
