package cv.igrp.platform.access_management.shared.i18n;

import cv.igrp.platform.access_management.shared.application.dto.InviteUserDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.GlobalExceptionHandler;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpErrorCode;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import jakarta.validation.Valid;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BE-4 (FR-12, FR-16), BE-5 (FR-20), BE-6 (FR-17), BE-7 (FR-18), BE-16 (FR-4).
 *
 * <p>MockMvc standalone setup with the production {@link GlobalExceptionHandler},
 * {@link IgrpLocaleResolver} (platform default {@code pt}), {@link Messages} and a
 * {@code MessageSource}-backed validator built from the real bundles and the real
 * {@code spring.messages.*} settings.
 *
 * <p>Fixture keys: {@code error.IGRP_AUTH_USER_NOT_FOUND_BY_ID} exists in en/pt/fr;
 * {@code error.IGRP_AUTH_INVITATION_EMAIL_MISMATCH} exists in en/pt only;
 * {@code error.IGRP_AUTH_ROLE_NOT_FOUND_BY_CODE} exists in en only. The test
 * classpath also carries an {@code i18n/messages_de.properties} (unsupported
 * language) that must never be selected.
 */
class GlobalExceptionHandlerI18nTest {

    @RestController
    static class ProbeController {

        @GetMapping("/probe/user/{id}")
        String userNotFound(@PathVariable String id) {
            throw IgrpResponseStatusException.of(IgrpErrorCode.IGRP_AUTH_USER_NOT_FOUND_BY_ID, id);
        }

        @GetMapping("/probe/email-mismatch")
        String emailMismatch() {
            throw IgrpResponseStatusException.of(IgrpErrorCode.IGRP_AUTH_INVITATION_EMAIL_MISMATCH);
        }

        @GetMapping("/probe/role/{code}")
        String roleNotFound(@PathVariable String code) {
            throw IgrpResponseStatusException.of(IgrpErrorCode.IGRP_AUTH_ROLE_NOT_FOUND_BY_CODE, code);
        }

        @GetMapping("/probe/quoted")
        String quoted() {
            throw IgrpResponseStatusException.of(IgrpErrorCode.IGRP_AUTH_FAVORITE_APPLICATION_ALREADY_EXISTS, "APP", "john");
        }

        @GetMapping("/probe/legacy")
        String legacy() {
            throw IgrpResponseStatusException.badRequest("Legacy free text");
        }

        @PostMapping("/probe/invite")
        String invite(@Valid @RequestBody InviteUserDTO dto) {
            return "ok";
        }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setDefaultLocale(IgrpLocale.PT.toLocale());
        mvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler(I18nTestSupport.messages()))
                .setLocaleResolver(new IgrpLocaleResolver(IgrpLocale.PT))
                .setValidator(I18nTestSupport.validator())
                .build();
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.setDefaultLocale(null);
        LocaleContextHolder.resetLocaleContext();
    }

    /** Adds Accept-Language (when given) and asks for JSON, as the Application Center does. */
    private static MockHttpServletRequestBuilder withLang(MockHttpServletRequestBuilder builder, String acceptLanguage) {
        builder.accept(MediaType.APPLICATION_JSON);
        return acceptLanguage == null ? builder : builder.header("Accept-Language", acceptLanguage);
    }

    // ─── BE-4: error.<CODE> per locale with per-key fallback ─────────────────

    @Test
    void frenchWithKeyPresentReturnsFrenchTextAndKeepsCode() throws Exception {
        mvc.perform(withLang(get("/probe/user/42"), "fr-FR,fr;q=0.9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IGRP_AUTH_USER_NOT_FOUND_BY_ID"))
                .andExpect(jsonPath("$.detail").value("Utilisateur introuvable avec l'id : 42"))
                .andExpect(jsonPath("$.title").value("Utilisateur introuvable avec l'id : 42"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void frenchWithoutKeyFallsBackToEnglishModuleDefault() throws Exception {
        mvc.perform(withLang(get("/probe/email-mismatch"), "fr"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IGRP_AUTH_INVITATION_EMAIL_MISMATCH"))
                .andExpect(jsonPath("$.detail").value("The provided email does not match the invitation"));
    }

    @Test
    void englishRequestGetsEnglish() throws Exception {
        mvc.perform(withLang(get("/probe/user/42"), "en-GB"))
                .andExpect(jsonPath("$.detail").value("User not found with id: 42"));
    }

    @Test
    void noAcceptLanguageUsesPlatformDefault() throws Exception {
        mvc.perform(withLang(get("/probe/email-mismatch"), null))
                .andExpect(jsonPath("$.detail").value("O email indicado não corresponde ao convite"))
                .andExpect(jsonPath("$.code").value("IGRP_AUTH_INVITATION_EMAIL_MISMATCH"));
    }

    @Test
    void platformDefaultWithoutKeyFallsBackToEnglish() throws Exception {
        mvc.perform(withLang(get("/probe/role/ADMIN"), null))
                .andExpect(jsonPath("$.detail").value("Role not found with code: ADMIN"));
    }

    @Test
    void unsupportedAcceptLanguageUsesPlatformDefaultAndNeverTheGermanBundle() throws Exception {
        // BE-16: messages_de.properties exists on the test classpath with this very key.
        mvc.perform(withLang(get("/probe/user/42"), "de-DE,de;q=0.9"))
                .andExpect(jsonPath("$.detail").value("Utilizador não encontrado com o id: 42"));
    }

    @Test
    void messageFormatQuotingRendersLikeTheEnumTemplate() throws Exception {
        String expected = IgrpErrorCode.IGRP_AUTH_FAVORITE_APPLICATION_ALREADY_EXISTS.formatMessage("APP", "john");
        mvc.perform(withLang(get("/probe/quoted"), "en"))
                .andExpect(jsonPath("$.detail").value(expected))
                .andExpect(jsonPath("$.detail").value("Application 'APP' is already a favorite of user 'john'"));
    }

    @Test
    void legacyFreeTextExceptionsAreReturnedUnchanged() throws Exception {
        mvc.perform(withLang(get("/probe/legacy"), "fr"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Legacy free text"));
    }

    // ─── BE-6: handler titles from error.handler.* ───────────────────────────

    @Test
    void validationTitleIsTranslated() throws Exception {
        mvc.perform(withLang(post("/probe/invite"), "fr")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Erreurs de validation"));
    }

    @Test
    void malformedJsonTitleIsTranslated() throws Exception {
        mvc.perform(withLang(post("/probe/invite"), "pt")
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Pedido JSON mal formado"));
        mvc.perform(withLang(post("/probe/invite"), "fr")
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(jsonPath("$.title").value("Malformed JSON request"));
    }

    // ─── BE-7: Bean Validation messages via the MessageSource ───────────────

    @Test
    void blankEmailViolationFollowsTheRequestedLocale() throws Exception {
        String body = "{\"email\":\"\"}";
        mvc.perform(withLang(post("/probe/invite"), "fr").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.errors.email").value("Le champ <email> est obligatoire"));
        mvc.perform(withLang(post("/probe/invite"), "pt").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.errors.email").value("O campo <email> é obrigatório"));
        mvc.perform(withLang(post("/probe/invite"), "en").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.errors.email").value("The field <email> is required"));
    }

    @Test
    void invalidEmailFallsBackToEnglishWhenTheLocaleLacksTheKey() throws Exception {
        String body = "{\"email\":\"not-an-email\"}";
        mvc.perform(withLang(post("/probe/invite"), "fr").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.errors.email").value("Invalid email format for field <email>"));
    }

    @Test
    void unsupportedInviteeLocaleIsAValidationError() throws Exception {
        String body = "{\"email\":\"john@nosi.cv\",\"locale\":\"de\"}";
        mvc.perform(withLang(post("/probe/invite"), "en").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.locale", startsWith("The field <locale> must be one of")));
        mvc.perform(withLang(post("/probe/invite"), "en").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"john@nosi.cv\",\"locale\":\"pt-CV\"}"))
                .andExpect(status().isOk());
    }

    // ─── BE-5: the JVM default locale has no effect ──────────────────────────

    @Nested
    class WithGermanJvmDefault {

        private Locale previous;

        @BeforeEach
        void germanJvm() {
            previous = Locale.getDefault();
            Locale.setDefault(Locale.GERMANY);
        }

        @AfterEach
        void restore() {
            Locale.setDefault(previous);
        }

        @Test
        void responsesAreUnchanged() throws Exception {
            frenchWithKeyPresentReturnsFrenchTextAndKeepsCode();
            frenchWithoutKeyFallsBackToEnglishModuleDefault();
            noAcceptLanguageUsesPlatformDefault();
            platformDefaultWithoutKeyFallsBackToEnglish();
            unsupportedAcceptLanguageUsesPlatformDefaultAndNeverTheGermanBundle();
            blankEmailViolationFollowsTheRequestedLocale();
        }
    }
}
