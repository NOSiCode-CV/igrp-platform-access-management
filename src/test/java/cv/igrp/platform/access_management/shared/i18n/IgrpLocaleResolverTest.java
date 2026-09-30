package cv.igrp.platform.access_management.shared.i18n;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** BE-3 (FR-15). */
class IgrpLocaleResolverTest {

    private final IgrpLocaleResolver resolver = new IgrpLocaleResolver(IgrpLocale.PT);

    @ParameterizedTest(name = "[{index}] ''{0}'' -> {1}")
    @CsvSource(delimiter = '|', value = {
            "fr-FR,fr;q=0.9                | fr",
            "de,fr;q=0.5,en;q=0.8           | en",   // q-value ordering, unsupported skipped
            "en;q=0.1,fr;q=0.9              | fr",   // order in the header does not matter
            "de-DE,de;q=0.9                 | pt",   // nothing supported -> platform default
            "de                             | pt",
            "pt-CV                          | pt",
            "*,en;q=0.5                     | en",   // wildcard is not a language
            "fr;q=0,en;q=0.2                | en",   // q=0 means 'not acceptable'
            "this is ;;; not a header       | pt",   // malformed -> platform default
    })
    void resolvesFirstSupportedTagByQValue(String header, String expected) {
        assertThat(resolver.resolve(header).tag()).isEqualTo(expected);
    }

    @Test
    void emptyOrMissingHeaderResolvesToPlatformDefault() {
        assertThat(resolver.resolve(null)).isEqualTo(IgrpLocale.PT);
        assertThat(resolver.resolve("")).isEqualTo(IgrpLocale.PT);
        assertThat(resolver.resolveLocale(new MockHttpServletRequest())).isEqualTo(Locale.forLanguageTag("pt"));
    }

    @Test
    void resolveLocaleReturnsTheBaseLanguageNeverARegion() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "fr-CA,fr;q=0.9");
        assertThat(resolver.resolveLocale(request)).isEqualTo(Locale.FRENCH);
    }

    @Test
    void platformDefaultIsConfigurable() {
        assertThat(new IgrpLocaleResolver(IgrpLocale.EN).resolve("de")).isEqualTo(IgrpLocale.EN);
    }

    @Test
    void localeCannotBeChangedThroughTheResolver() {
        assertThatThrownBy(() -> resolver.setLocale(new MockHttpServletRequest(), null, Locale.FRENCH))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
