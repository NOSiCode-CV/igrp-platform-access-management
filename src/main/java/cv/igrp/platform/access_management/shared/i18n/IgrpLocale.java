package cv.igrp.platform.access_management.shared.i18n;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The fixed list of iGRP platform languages (FR-1). Not configurable.
 *
 * <p>Each constant binds a base language tag (the only form ever stored or sent
 * over the wire, e.g. {@code metadata.locale}) and a fixed formatting region used
 * for dates, numbers and collation (FR-22).
 */
public enum IgrpLocale {

    PT("pt", "pt-CV"),
    EN("en", "en-GB"),
    FR("fr", "fr-FR");

    private final String tag;
    private final Locale locale;
    private final Locale region;

    IgrpLocale(String tag, String regionTag) {
        this.tag = tag;
        this.locale = Locale.forLanguageTag(tag);
        this.region = Locale.forLanguageTag(regionTag);
    }

    /** Base language tag, e.g. {@code "pt"}. */
    public String tag() {
        return tag;
    }

    /** Base-language {@link Locale} used for message lookup, e.g. {@code pt}. */
    public Locale toLocale() {
        return locale;
    }

    /** Fixed formatting region for this language, e.g. {@code pt-CV}. */
    public Locale region() {
        return region;
    }

    /**
     * Normalizes a language tag to a platform language. Regional variants and
     * underscore/upper-case spellings collapse to the base language
     * ({@code pt-CV}, {@code pt_PT}, {@code PT} → {@link #PT}) (FR-8).
     *
     * @return the matching platform language, or empty for blank / unsupported tags
     */
    public static Optional<IgrpLocale> fromTag(String tag) {
        if (tag == null) {
            return Optional.empty();
        }
        String normalized = tag.trim().replace('_', '-');
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        String language = Locale.forLanguageTag(normalized).getLanguage();
        if (language.isEmpty()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(l -> l.tag.equals(language))
                .findFirst();
    }

    /** Same as {@link #fromTag(String)} for an already-parsed {@link Locale}. */
    public static Optional<IgrpLocale> fromLocale(Locale locale) {
        return locale == null ? Optional.empty() : fromTag(locale.toLanguageTag());
    }

    /** Comma-separated list of supported tags, for error messages ({@code "pt, en, fr"}). */
    public static String supportedTags() {
        return Arrays.stream(values()).map(IgrpLocale::tag).collect(Collectors.joining(", "));
    }
}
