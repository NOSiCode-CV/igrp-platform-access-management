package cv.igrp.platform.access_management.shared.i18n;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/** FR-12 fallback chain, FR-4 / BE-16 unsupported bundles, FR-20 no JVM-locale leakage. */
class MessagesTest {

    private final Messages messages = I18nTestSupport.messages();

    @AfterEach
    void reset() {
        LocaleContextHolder.resetLocaleContext();
        LocaleContextHolder.setDefaultLocale(null);
    }

    @Test
    void requestedLocaleThenModuleDefaultThenKey() {
        assertThat(messages.get(IgrpLocale.FR, "error.handler.validationErrors")).isEqualTo("Erreurs de validation");
        assertThat(messages.get(IgrpLocale.FR, "error.handler.malformedJson")).isEqualTo("Malformed JSON request");
        assertThat(messages.get(IgrpLocale.FR, "no.such.key")).isEqualTo("no.such.key");
        assertThat(messages.getOrDefault("no.such.key", "fallback text")).isEqualTo("fallback text");
    }

    @Test
    void argumentsAreFormatted() {
        assertThat(messages.get(IgrpLocale.PT, "error.IGRP_AUTH_USER_NOT_FOUND_BY_ID", "7"))
                .isEqualTo("Utilizador não encontrado com o id: 7");
    }

    @Test
    void currentLocaleComesFromTheLocaleContext() {
        LocaleContextHolder.setLocale(Locale.FRENCH);
        assertThat(messages.currentLocale()).isEqualTo(IgrpLocale.FR);
        assertThat(messages.get("error.handler.validationErrors")).isEqualTo("Erreurs de validation");
    }

    @Test
    void unsupportedLocaleNeverSelectsItsBundle() {
        // messages_de.properties exists on the test classpath with this key.
        LocaleContextHolder.setLocale(Locale.GERMAN);
        assertThat(messages.currentLocale()).isEqualTo(IgrpLocale.PT);
        assertThat(messages.get("error.IGRP_AUTH_USER_NOT_FOUND_BY_ID", "1"))
                .isEqualTo("Utilizador não encontrado com o id: 1");
        assertThat(messages.get(IgrpLocale.EN, "validation.email.required"))
                .isEqualTo("The field <email> is required");
    }

    @Test
    void jvmDefaultLocaleDoesNotLeakIntoResolution() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            // English is the base bundle: with fallback-to-system-locale=false it is not
            // shadowed by messages_de.properties.
            assertThat(messages.get(IgrpLocale.EN, "error.IGRP_AUTH_USER_NOT_FOUND_BY_ID", "1"))
                    .isEqualTo("User not found with id: 1");
            assertThat(messages.get(IgrpLocale.FR, "validation.email.required"))
                    .isEqualTo("Le champ <email> est obligatoire");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void unsupportedBundlesAreReported() {
        assertThat(I18nConfig.unsupportedBundles())
                .contains("messages_de.properties")
                .doesNotContain("messages_pt.properties", "messages_fr.properties");
    }
}
