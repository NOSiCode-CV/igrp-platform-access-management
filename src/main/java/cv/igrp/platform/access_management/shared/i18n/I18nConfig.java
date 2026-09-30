package cv.igrp.platform.access_management.shared.i18n;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.LocaleResolver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wires the Access Management i18n infrastructure:
 * <ul>
 *   <li>{@code localeResolver} — {@link IgrpLocaleResolver} (Accept-Language ∩ platform list → platform default);</li>
 *   <li>Bean Validation backed by the application {@link MessageSource}, so
 *       {@code {validation.*}} keys resolve from {@code i18n/messages*.properties} (FR-18);</li>
 *   <li>{@link Messages} helper.</li>
 * </ul>
 * It also binds the platform default as the {@link LocaleContextHolder} default, so
 * code running outside a request never falls back to the JVM locale (FR-20), and
 * warns about bundles for languages outside the platform list (FR-4).
 *
 * <p>The {@code MessageSource} itself is Spring Boot's auto-configured one
 * ({@code spring.messages.*} in {@code application.properties}).
 */
@Configuration
@EnableConfigurationProperties(I18nProperties.class)
public class I18nConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(I18nConfig.class);

    static final String BUNDLE_PATTERN = "classpath*:i18n/messages_*.properties";
    private static final Pattern BUNDLE_SUFFIX = Pattern.compile("messages_([A-Za-z]{2,8}(?:_[A-Za-z0-9]+)*)\\.properties$");

    private final I18nProperties properties;

    public I18nConfig(I18nProperties properties) {
        this.properties = properties;
        properties.validate();
        LocaleContextHolder.setDefaultLocale(properties.platformDefault().toLocale());
        LOGGER.info("i18n: module default locale={}, platform default locale={}",
                properties.moduleDefault().tag(), properties.platformDefault().tag());
        for (String bundle : unsupportedBundles()) {
            LOGGER.warn("i18n: ignoring translation file '{}' — its language is not a platform language [{}]",
                    bundle, IgrpLocale.supportedTags());
        }
    }

    @Bean(name = DispatcherServlet.LOCALE_RESOLVER_BEAN_NAME)
    public LocaleResolver localeResolver() {
        return new IgrpLocaleResolver(properties.platformDefault());
    }

    @Bean
    public LocalValidatorFactoryBean validator(MessageSource messageSource) {
        LocalValidatorFactoryBean factory = new LocalValidatorFactoryBean();
        factory.setValidationMessageSource(messageSource);
        return factory;
    }

    @Bean
    public Messages messages(MessageSource messageSource) {
        return new Messages(messageSource, properties.platformDefault());
    }

    /** File names of {@code i18n/messages_<lang>.properties} bundles whose language is unsupported. */
    static List<String> unsupportedBundles() {
        List<String> unsupported = new ArrayList<>();
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(BUNDLE_PATTERN);
            for (Resource resource : resources) {
                String name = resource.getFilename();
                if (name == null) {
                    continue;
                }
                Matcher m = BUNDLE_SUFFIX.matcher(name);
                if (m.find() && IgrpLocale.fromTag(m.group(1)).isEmpty()) {
                    unsupported.add(name);
                }
            }
        } catch (IOException ex) {
            LOGGER.debug("i18n: could not scan translation bundles", ex);
        }
        return unsupported;
    }
}
