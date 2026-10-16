package cv.igrp.platform.access_management.shared.i18n;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/** BE-1 (FR-1, FR-8). */
class IgrpLocaleTest {

    @ParameterizedTest
    @ValueSource(strings = {"pt", "pt-CV", "pt_PT", "PT", "pt-BR", " pt-cv ", "pt_pt"})
    void portugueseVariantsNormalizeToPt(String tag) {
        assertThat(IgrpLocale.fromTag(tag)).contains(IgrpLocale.PT);
    }

    @Test
    void englishAndFrenchVariantsNormalize() {
        assertThat(IgrpLocale.fromTag("en-US")).contains(IgrpLocale.EN);
        assertThat(IgrpLocale.fromTag("EN_gb")).contains(IgrpLocale.EN);
        assertThat(IgrpLocale.fromTag("fr-CA")).contains(IgrpLocale.FR);
        assertThat(IgrpLocale.fromTag("fr")).contains(IgrpLocale.FR);
    }

    @ParameterizedTest
    @ValueSource(strings = {"de", "de-DE", "es", "*", "x", "123", "zz-ZZ"})
    void unsupportedTagsAreEmpty(String tag) {
        assertThat(IgrpLocale.fromTag(tag)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void blankTagsAreEmpty(String tag) {
        assertThat(IgrpLocale.fromTag(tag)).isEmpty();
    }

    @Test
    void platformListIsFixed() {
        assertThat(IgrpLocale.values()).extracting(IgrpLocale::tag).containsExactly("pt", "en", "fr");
        assertThat(IgrpLocale.supportedTags()).isEqualTo("pt, en, fr");
    }

    @Test
    void formattingRegionsAreFixed() {
        assertThat(IgrpLocale.PT.region()).isEqualTo(Locale.forLanguageTag("pt-CV"));
        assertThat(IgrpLocale.EN.region()).isEqualTo(Locale.forLanguageTag("en-GB"));
        assertThat(IgrpLocale.FR.region()).isEqualTo(Locale.forLanguageTag("fr-FR"));
        assertThat(IgrpLocale.PT.toLocale()).isEqualTo(Locale.forLanguageTag("pt"));
    }

    @Test
    void fromLocaleUsesTheLanguage() {
        assertThat(IgrpLocale.fromLocale(Locale.FRANCE)).contains(IgrpLocale.FR);
        assertThat(IgrpLocale.fromLocale(Locale.GERMAN)).isEmpty();
        assertThat(IgrpLocale.fromLocale(null)).isEmpty();
    }

    @Test
    void supportedLocaleConstraintAcceptsBlankAndPlatformTagsOnly() {
        SupportedLocale.Validator validator = new SupportedLocale.Validator();
        assertThat(validator.isValid(null, null)).isTrue();
        assertThat(validator.isValid("", null)).isTrue();
        assertThat(validator.isValid("pt-CV", null)).isTrue();
        assertThat(validator.isValid("de", null)).isFalse();
    }
}
