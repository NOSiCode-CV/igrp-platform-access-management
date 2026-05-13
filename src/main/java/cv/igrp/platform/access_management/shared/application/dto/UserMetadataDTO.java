package cv.igrp.platform.access_management.shared.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Free-form user metadata persisted on {@code t_user.metadata} and surfaced
 * through OAuth2 identity-related APIs as well as injected into issued JWT
 * tokens by the authorization server.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserMetadataDTO {

    private String userId;
    private Map<String, Object> metadata;
}
