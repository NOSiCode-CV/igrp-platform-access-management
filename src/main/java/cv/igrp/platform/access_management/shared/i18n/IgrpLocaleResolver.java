package cv.igrp.platform.access_management.shared.i18n;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.LocaleResolver;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Resolves the requested locale of an API call (FR-15).
 *
 * <p>Reads {@code Accept-Language}, walks its language ranges in q-value order
 * and returns the first one that normalizes to a platform language
 * ({@link IgrpLocale}). Anything else — no header, a malformed header, only
 * unsupported languages — resolves to the platform default. The returned
 * {@link Locale} is always a base language ({@code pt}, {@code en}, {@code fr}),
 * never a region, so the JVM / server OS locale can never leak in (FR-20).
 *
 * <p>The API is stateless: the locale cannot be changed through this resolver.
 */
public class IgrpLocaleResolver implements LocaleResolver {

    private final IgrpLocale platformDefault;

    public IgrpLocaleResolver(IgrpLocale platformDefault) {
        this.platformDefault = platformDefault;
    }

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        return resolve(request.getHeader(HttpHeaders.ACCEPT_LANGUAGE)).toLocale();
    }

    /** Resolves a raw {@code Accept-Language} header value to a platform language. */
    public IgrpLocale resolve(String acceptLanguage) {
        return fromHeader(acceptLanguage).orElse(platformDefault);
    }

    static Optional<IgrpLocale> fromHeader(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return Optional.empty();
        }
        List<Locale.LanguageRange> ranges;
        try {
            // parse() returns the ranges sorted by descending weight and drops q=0 entries.
            ranges = Locale.LanguageRange.parse(acceptLanguage);
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
        for (Locale.LanguageRange range : ranges) {
            if (range.getWeight() <= 0) {
                continue;
            }
            Optional<IgrpLocale> match = IgrpLocale.fromTag(range.getRange());
            if (match.isPresent()) {
                return match;
            }
        }
        return Optional.empty();
    }

    @Override
    public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        throw new UnsupportedOperationException(
                "The API locale is resolved from Accept-Language and cannot be changed per request");
    }
}
