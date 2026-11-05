package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.framework.core.domain.CommandBus;
import cv.igrp.framework.core.domain.QueryBus;
import cv.igrp.platform.access_management.shared.domain.exceptions.GlobalExceptionHandler;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpErrorCode;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.i18n.I18nTestSupport;
import cv.igrp.platform.access_management.shared.i18n.IgrpLocale;
import cv.igrp.platform.access_management.shared.i18n.IgrpLocaleResolver;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import cv.igrp.platform.access_management.users.interfaces.rest.UserController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** BE-9 (FR-10, FR-11): PUT /api/users/me/locale. */
class UpdateCurrentUserLocaleCommandHandlerTest {

    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";

    private final AuthenticationHelper authenticationHelper = mock(AuthenticationHelper.class);
    private final IGRPUserEntityRepository userRepository = mock(IGRPUserEntityRepository.class);
    private final UpdateCurrentUserLocaleCommandHandler handler =
            new UpdateCurrentUserLocaleCommandHandler(authenticationHelper, userRepository);

    private IGRPUserEntity user;

    @BeforeEach
    void setUp() {
        user = new IGRPUserEntity();
        user.setId(USER_ID);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("given_name", "John");
        metadata.put("billing.plan", "gold");
        user.setMetadata(metadata);
        when(authenticationHelper.getSub()).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    @Test
    void storesTheLocaleAndKeepsOtherMetadataKeys() {
        ResponseEntity<Void> response = handler.handle(new UpdateCurrentUserLocaleCommand("fr"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(user.getMetadata())
                .containsEntry("locale", "fr")
                .containsEntry("given_name", "John")
                .containsEntry("billing.plan", "gold");
        verify(userRepository).save(user);
    }

    @Test
    void regionalTagsAreStoredAsTheBaseLanguage() {
        handler.handle(new UpdateCurrentUserLocaleCommand("pt-CV"));
        assertThat(user.getMetadata()).containsEntry("locale", "pt");
    }

    @Test
    void replacesAPreviousPreference() {
        user.getMetadata().put("locale", "pt");
        handler.handle(new UpdateCurrentUserLocaleCommand("en"));
        assertThat(user.getMetadata()).containsEntry("locale", "en");
    }

    @Test
    void worksWhenTheUserHasNoMetadataYet() {
        user.setMetadata(null);
        handler.handle(new UpdateCurrentUserLocaleCommand("en"));
        assertThat(user.getMetadata()).containsExactly(Map.entry("locale", "en"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"de", "es-ES", "xx", "  "})
    void unsupportedValuesAreRejectedWith400InvalidLocale(String value) {
        assertThatThrownBy(() -> handler.handle(new UpdateCurrentUserLocaleCommand(value)))
                .isInstanceOfSatisfying(IgrpResponseStatusException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(IgrpErrorCode.INVALID_LOCALE);
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
        verify(userRepository, never()).save(any());
        assertThat(user.getMetadata()).doesNotContainKey("locale");
    }

    @Test
    void unknownUserIsRejected() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> handler.handle(new UpdateCurrentUserLocaleCommand("fr")))
                .isInstanceOfSatisfying(IgrpResponseStatusException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(IgrpErrorCode.IGRP_AUTH_USER_NOT_FOUND_BY_EXTERNAL_ID));
    }

    // ─── HTTP contract through the real controller + exception handler ───────

    private MockMvc mvc() {
        CommandBus commandBus = mock(CommandBus.class);
        when(commandBus.send(any())).thenAnswer(inv -> handler.handle(inv.getArgument(0)));
        UserController controller = new UserController(mock(QueryBus.class), commandBus, authenticationHelper);
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(I18nTestSupport.messages()))
                .setLocaleResolver(new IgrpLocaleResolver(IgrpLocale.PT))
                .setValidator(I18nTestSupport.validator())
                .build();
    }

    @Test
    void putReturns204() throws Exception {
        mvc().perform(put("/api/users/me/locale").contentType(MediaType.APPLICATION_JSON).content("{\"locale\":\"fr\"}"))
                .andExpect(status().isNoContent());
        assertThat(user.getMetadata()).containsEntry("locale", "fr");
    }

    @Test
    void putWithUnsupportedLocaleReturns400InvalidLocaleInTheRequestLanguage() throws Exception {
        mvc().perform(put("/api/users/me/locale").accept(MediaType.APPLICATION_JSON).header("Accept-Language", "pt")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"locale\":\"de\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LOCALE"))
                .andExpect(jsonPath("$.detail").value("Idioma não suportado: de. Idiomas suportados: pt, en, fr"));
        mvc().perform(put("/api/users/me/locale").accept(MediaType.APPLICATION_JSON).header("Accept-Language", "en")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"locale\":\"de\"}"))
                .andExpect(jsonPath("$.detail").value("Unsupported locale: de. Supported locales: pt, en, fr"));
    }
}
