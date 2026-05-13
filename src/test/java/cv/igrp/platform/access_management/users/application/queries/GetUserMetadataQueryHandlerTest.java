package cv.igrp.platform.access_management.users.application.queries;

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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetUserMetadataQueryHandlerTest {

    @Mock private IGRPUserEntityRepository userRepository;
    private GetUserMetadataQueryHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetUserMetadataQueryHandler(userRepository);
    }

    private static final String UID1 = "00000000-0000-0000-0000-000000000001";
    private static final String UID99 = "00000000-0000-0000-0000-000000000099";

    @Test
    void returnsStoredMetadata() {
        IGRPUserEntity user = new IGRPUserEntity();
        user.setId(UID1);
        Map<String, Object> md = new LinkedHashMap<>();
        md.put("locale", "pt-CV");
        user.setMetadata(md);
        when(userRepository.findById(UID1)).thenReturn(Optional.of(user));

        ResponseEntity<UserMetadataDTO> response = handler.handle(new GetUserMetadataQuery(UID1));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(UID1, response.getBody().getUserId());
        assertEquals("pt-CV", response.getBody().getMetadata().get("locale"));
    }

    @Test
    void throwsWhenUserMissing() {
        when(userRepository.findById(UID99)).thenReturn(Optional.empty());
        assertThrows(IgrpResponseStatusException.class,
                () -> handler.handle(new GetUserMetadataQuery(UID99)));
    }
}
