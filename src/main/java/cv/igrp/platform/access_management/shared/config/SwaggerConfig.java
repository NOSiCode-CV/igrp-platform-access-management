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
                Paste your access token (JWT) in the **Value** field below and click
                **Authorize**. Swagger adds the `Bearer ` prefix for you — paste the
                raw token only, with no quotes.

                Getting a token: sign in to the Application Center and copy the access
                token from your session, or request one from the authorization server's
                `/oauth2/token` endpoint.

                Tokens expire. On a sudden `401`, get a fresh token and authorize again.
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
