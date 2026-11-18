package cv.igrp.platform.access_management.page_access.domain.service;

import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity.OAuthClientEntity;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository.OAuthClientJpaRepository;
import cv.igrp.platform.access_management.page_access.application.dto.ManifestRegistrationRequestDTO;
import cv.igrp.platform.access_management.page_access.application.dto.ManifestRegistrationRequestDTO.OptionalPermissionDTO;
import cv.igrp.platform.access_management.page_access.application.dto.ManifestRegistrationRequestDTO.PageManifestDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import cv.igrp.platform.access_management.shared.security.ServiceAccountTokenClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The checks a registration passes before anything is stored (v3-27, #35). */
class PageManifestServiceTest {

    private static final String APP = "APP_X";

    private final OAuthClientJpaRepository clients = mock(OAuthClientJpaRepository.class);
    private final AuthenticationHelper auth = mock(AuthenticationHelper.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PageManifestService service = new PageManifestService(clients, auth, jdbc);

    @BeforeEach
    void serviceAccountOfAppX() {
        when(auth.getJwtToken()).thenReturn(token(ServiceAccountTokenClaims.PRINCIPAL_TYPE_SERVICE_ACCOUNT, "cli"));
        ApplicationEntity application = new ApplicationEntity();
        application.setId(7);
        application.setCode(APP);
        OAuthClientEntity client = new OAuthClientEntity();
        client.setApplication(application);
        when(clients.findByClientId("cli")).thenReturn(Optional.of(client));
    }

    @Test
    @DisplayName("A user token is refused with 403")
    void userToken() {
        when(auth.getJwtToken()).thenReturn(token(null, null));
        assertStatus(register(page("a", "/a")), HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("A client with no application is refused with 409")
    void clientWithoutApplication() {
        when(clients.findByClientId("cli")).thenReturn(Optional.of(new OAuthClientEntity()));
        assertStatus(register(page("a", "/a")), HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("Another application's manifest, a duplicate page or path, a bad page key: 400 before any write")
    void badManifests() {
        assertStatus(register(new PageManifestDTO("OTHER", "app.OTHER.a", "/a", List.of(), List.of())),
                HttpStatus.BAD_REQUEST);
        assertStatus(register(page("a", "/a"), page("a", "/b")), HttpStatus.BAD_REQUEST);
        assertStatus(register(page("a", "/a"), page("b", "/a")), HttpStatus.BAD_REQUEST);
        assertStatus(register(new PageManifestDTO(APP, "app.APP_X.", "/a", List.of(), List.of())),
                HttpStatus.BAD_REQUEST);
        assertStatus(register(new PageManifestDTO(APP, "app.APP_X.a b", "/a", List.of(), List.of())),
                HttpStatus.BAD_REQUEST);
        assertStatus(register(new PageManifestDTO(APP, "app.APP_X.a", "/a",
                List.of("svc.x.read"), List.of(new OptionalPermissionDTO("svc.x.read", "X")))), HttpStatus.BAD_REQUEST);
        assertStatus(register(new PageManifestDTO(APP, "app.APP_X.a", "/a", List.of("svc.x.fly"), null)),
                HttpStatus.BAD_REQUEST);
        verifyNoInteractions(jdbc);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/faturas", "/settings/users/[id]", "/(group)/docs/[[...slug]]", "/a/[...rest]"})
    @DisplayName("App Router paths are valid page paths")
    void safePaths(String path) {
        assertThat(PageManifestService.isSafePath(path)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"x", "/a/../b", "/a/./b", "/a//b", "/a\\b", "/a%2Fb", "/[...rest]/x"})
    @DisplayName("Relative, dot, empty, encoded or misplaced catch-all segments are refused")
    void unsafePaths(String path) {
        assertThat(PageManifestService.isSafePath(path)).isFalse();
    }

    @Test
    @DisplayName("A key of any service is <service>.<resource>.<action>")
    void keys() {
        assertThat(PageManifestService.keyProblem("access-management-api.users.list")).isNull();
        assertThat(PageManifestService.keyProblem("svc.faturas.margem.read")).isNull();
        assertThat(PageManifestService.keyProblem("nodot")).isNotNull();
        assertThat(PageManifestService.keyProblem(".x.read")).isNotNull();
        assertThat(PageManifestService.keyProblem("igrp.users.list")).isNotNull();
        assertThat(PageManifestService.keyProblem("svc.read")).isNotNull();
        assertThat(PageManifestService.keyProblem(null)).isNotNull();
    }

    private Runnable register(PageManifestDTO... pages) {
        return () -> service.register(new ManifestRegistrationRequestDTO("1.0.0", List.of(pages)));
    }

    private static PageManifestDTO page(String name, String path) {
        return new PageManifestDTO(APP, "app." + APP + "." + name, path, List.of(), List.of());
    }

    private static void assertStatus(Runnable call, HttpStatus status) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(IgrpResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(status));
    }

    private static Jwt token(String principalType, String clientId) {
        Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "none").subject("s");
        if (principalType != null) {
            builder.claim(ServiceAccountTokenClaims.CLAIM_PRINCIPAL_TYPE, principalType);
        }
        if (clientId != null) {
            builder.claim(ServiceAccountTokenClaims.CLAIM_CLIENT_ID, clientId);
        }
        return builder.build();
    }
}
