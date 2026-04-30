package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.platform.access_management.shared.application.dto.UserMetadataDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateUserMetadataCommandHandlerTest {

    @Mock private IGRPUserEntityRepository userRepository;
    private UpdateUserMetadataCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new UpdateUserMetadataCommandHandler(userRepository);
    }

    @Test
    void replacesMetadata() {
        IGRPUserEntity user = new IGRPUserEntity();
        user.setId(3);
        user.setMetadata(new LinkedHashMap<>(Map.of("old", "value")));
        when(userRepository.findById(3)).thenReturn(Optional.of(user));
        when(userRepository.save(any(IGRPUserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> incoming = new LinkedHashMap<>();
        incoming.put("locale", "en-US");
        incoming.put("favorite", 42);

        ResponseEntity<UserMetadataDTO> resp = handler.handle(new UpdateUserMetadataCommand(3, incoming));

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("en-US", resp.getBody().getMetadata().get("locale"));
        assertFalse(resp.getBody().getMetadata().containsKey("old"),
                "metadata must be replaced, not merged");
    }

    @Test
    void throwsWhenUserMissing() {
        when(userRepository.findById(99)).thenReturn(Optional.empty());
        assertThrows(IgrpResponseStatusException.class,
                () -> handler.handle(new UpdateUserMetadataCommand(99, Map.of("x", "y"))));
    }
}
