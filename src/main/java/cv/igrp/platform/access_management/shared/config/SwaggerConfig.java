package cv.igrp.platform.access_management.shared.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@OpenAPIDefinition(
        security = { @SecurityRequirement(name = "bearerAuth") }
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = """
                Bearer JWT issued by the iGRP authorization server.

                ### Access-token claims

                Standard OAuth2/OIDC:
                - **sub** (string) — internal user id (subject).
                - **jti** (string) — unique token id. Mirrored to `SessionEntity.jti`.
                - **iat** (number, seconds since epoch) — token issuance time. Compared
                  against the user-wide `tokens_not_valid_before` floor on every
                  authenticated request (Phase F1).
                - **exp** (number, seconds since epoch) — token-level expiry.
                - **iss** (string) — issuer (the authorization server URL).
                - **aud** (string | string[]) — audience(s) the token is intended for.

                iGRP session-binding (NEW, mandatory on user-bound tokens):
                - **sid** (string, UUID) — canonical session identifier. Equal to
                  `t_user_session.session_id`. The `SessionEnforcementFilter` rejects
                  the request when the row is no longer ACTIVE or has expired.
                - **device_id** (string) — opaque device identifier. Read from the
                  `X-Device-Id` request header on the token endpoint, or derived as a
                  SHA-256 hash of `(User-Agent, client IP, client_id)` when not
                  supplied. Two concurrent sessions cannot share `(user, device_id)`.

                iGRP authorization enrichment (added by `ClaimsEnrichmentService`):
                - **selectedRole** (string) — the user's currently-active role code.
                - **org** (object) — organizational unit context (department / scope).
                - **permissions** (string[]) — flattened permission names granted via
                  the active role.
                - **resource_access** (object) — Keycloak-shaped per-client role map
                  for downstream resource servers.
                - **identity claims** — user profile fields (name, email,
                  email_verified, preferred_username, phone_number, picture, nic, etc.)
                  copied from `IGRPUserEntity` for the configured client.

                Tokens issued via `client_credentials` (M2M) carry neither `sid` nor
                `device_id` and bypass the session enforcement filter.

                **Phase F1 — token validity floor:** the user record carries a
                `tokens_not_valid_before` instant. The enforcement filter rejects any
                JWT with `iat` strictly before this floor, even when the bound session
                row is still ACTIVE. The floor is bumped by password reset / forced
                re-auth flows.
                """
)
public class SwaggerConfig {

    @Value("${openapi.server.api:}")
    private String serverApiUrl; // fallback empty if not set

    @Bean
    public OpenApiCustomizer customOpenApi() {
        return openApi -> {
            if (serverApiUrl != null && !serverApiUrl.isBlank()) {
                openApi.getServers().clear();
                openApi.addServersItem(
                        new Server().url(serverApiUrl).description("Configured Server")
                );
            }
            // else: leave servers alone, springdoc auto-detects.

            if (openApi.getInfo() == null) {
                openApi.setInfo(new Info());
            }
            Info info = openApi.getInfo();
            if (info.getTitle() == null || info.getTitle().isBlank()) {
                info.setTitle("iGRP Access Management API");
            }

            mergeTag(openApi, "Session Management",
                    "User-facing session lifecycle endpoints (introspect current session, "
                            + "refresh, rotate). The `/api/session/check` endpoint is the only "
                            + "session endpoint exempt from the `SessionEnforcementFilter`, so "
                            + "callers holding a JWT for a revoked session can still receive a "
                            + "structured `valid=false` answer.");
            mergeTag(openApi, "Admin Session Management",
                    "Administrative session inspection and revocation. Requires the "
                            + "`IGRP_SESSION_ADMIN` permission.");
            mergeTag(openApi, "Admin User Session Management",
                    "Per-user administrative session operations: kill one session, "
                            + "logout-all, force re-authentication (Phase F1).");
        };
    }

    private static void mergeTag(io.swagger.v3.oas.models.OpenAPI openApi,
                                 String name, String description) {
        if (openApi.getTags() == null) {
            openApi.setTags(new java.util.ArrayList<>());
        }
        List<Tag> tags = openApi.getTags();
        for (Tag tag : tags) {
            if (name.equals(tag.getName())) {
                if (tag.getDescription() == null || tag.getDescription().isBlank()) {
                    tag.setDescription(description);
                }
                return;
            }
        }
        tags.add(new Tag().name(name).description(description));
    }
}
