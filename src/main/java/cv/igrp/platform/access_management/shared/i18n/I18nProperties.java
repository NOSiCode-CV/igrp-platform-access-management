package cv.igrp.platform.access_management.shared.i18n;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code igrp.i18n.*} settings.
 *
 * <ul>
 *   <li>{@code default-locale} — the <b>module default</b>: the language whose bundle
 *       ({@code messages.properties}) is complete. Second step of the message
 *       fallback chain (FR-12).</li>
 *   <li>{@code platform-default-locale} — the <b>platform default</b>: only used to
 *       pick the requested locale when nothing else is known (FR-6/FR-15). It never
 *       takes part in message fallback (FR-13).</li>
 * </ul>
 *
 * Both values must be platform languages ({@link IgrpLocale}); anything else fails
 * application startup (FR-3).
 */
@ConfigurationProperties(prefix = "igrp.i18n")
public class I18nProperties implements InitializingBean {

    private String defaultLocale = IgrpLocale.EN.tag();

    private String platformDefaultLocale = IgrpLocale.PT.tag();

    public String getDefaultLocale() {
        return defaultLocale;
    }

    public void setDefaultLocale(String defaultLocale) {
        this.defaultLocale = defaultLocale;
    }

    public String getPlatformDefaultLocale() {
        return platformDefaultLocale;
    }

    public void setPlatformDefaultLocale(String platformDefaultLocale) {
        this.platformDefaultLocale = platformDefaultLocale;
    }

    /** Module default as a platform language. */
    public IgrpLocale moduleDefault() {
        return require("igrp.i18n.default-locale", defaultLocale);
    }

    /** Platform default as a platform language. */
    public IgrpLocale platformDefault() {
        return require("igrp.i18n.platform-default-locale", platformDefaultLocale);
    }

    @Override
    public void afterPropertiesSet() {
        validate();
    }

    /** Fails fast (FR-3) when either value is not a platform language. */
    public void validate() {
        moduleDefault();
        platformDefault();
    }

    private static IgrpLocale require(String property, String value) {
        return IgrpLocale.fromTag(value).orElseThrow(() -> new IllegalStateException(
                "Invalid " + property + "='" + value + "': must be one of the platform languages ["
                        + IgrpLocale.supportedTags() + "]"));
    }
}
