package cv.igrp.platform.access_management.shared.i18n;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thin wrapper over the application {@link MessageSource}.
 *
 * <p>Resolution chain per key (FR-12): requested locale bundle → module default
 * bundle ({@code messages.properties}) → the key itself (or a caller-supplied
 * default) plus a one-time WARN per missing key.
 *
 * <p>The requested locale is taken from {@link LocaleContextHolder} (bound by the
 * {@link IgrpLocaleResolver} inside a request; the platform default outside a
 * request) and always normalized through {@link IgrpLocale}, so a locale outside
 * the platform list — including the JVM default — can never select a bundle
 * (FR-4, FR-20).
 */
public class Messages {

    private static final Logger LOGGER = LoggerFactory.getLogger(Messages.class);

    private final MessageSource messageSource;
    private final IgrpLocale platformDefault;
    private final Set<String> warnedKeys = ConcurrentHashMap.newKeySet();

    public Messages(MessageSource messageSource, IgrpLocale platformDefault) {
        this.messageSource = messageSource;
        this.platformDefault = platformDefault;
    }

    /** The platform language of the current thread (request locale or platform default). */
    public IgrpLocale currentLocale() {
        return IgrpLocale.fromLocale(LocaleContextHolder.getLocale()).orElse(platformDefault);
    }

    /** Resolves {@code key} in the current locale; returns the key itself when missing. */
    public String get(String key, Object... args) {
        return get(currentLocale(), key, args);
    }

    /** Resolves {@code key} in {@code locale}; returns the key itself when missing. */
    public String get(IgrpLocale locale, String key, Object... args) {
        return find(locale, key, args).orElseGet(() -> {
            warnMissing(key);
            return key;
        });
    }

    /**
     * Resolves {@code key} in the current locale; returns {@code defaultText}
     * (already formatted by the caller) when the key is missing from every bundle.
     */
    public String getOrDefault(String key, String defaultText, Object... args) {
        return find(currentLocale(), key, args).orElseGet(() -> {
            warnMissing(key);
            return defaultText;
        });
    }

    /** Resolves {@code key} without any fallback text or warning. */
    public Optional<String> find(IgrpLocale locale, String key, Object... args) {
        if (key == null) {
            return Optional.empty();
        }
        Locale target = (locale != null ? locale : platformDefault).toLocale();
        try {
            return Optional.of(messageSource.getMessage(key, args == null || args.length == 0 ? null : args, target));
        } catch (NoSuchMessageException ex) {
            return Optional.empty();
        }
    }

    private void warnMissing(String key) {
        if (warnedKeys.add(key)) {
            LOGGER.warn("Missing i18n message key '{}' in every bundle (requested and module default)", key);
        }
    }
}
