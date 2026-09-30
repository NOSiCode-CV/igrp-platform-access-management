package cv.igrp.platform.access_management.shared.i18n;

import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Builds the i18n beans exactly as the application does, without a Spring context
 * (the suite has no working {@code @SpringBootTest}). The {@code MessageSource} is
 * configured from the {@code spring.messages.*} entries of the real
 * {@code application.properties}, so a change there is reflected here.
 */
public final class I18nTestSupport {

    private I18nTestSupport() {
    }

    public static Properties applicationProperties() {
        try (InputStream in = I18nTestSupport.class.getClassLoader().getResourceAsStream("application.properties")) {
            Properties props = new Properties();
            props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return props;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Mirrors Spring Boot's MessageSourceAutoConfiguration for the configured spring.messages.* values. */
    public static ResourceBundleMessageSource messageSource() {
        Properties props = applicationProperties();
        ResourceBundleMessageSource ms = new ResourceBundleMessageSource();
        ms.setBasenames(props.getProperty("spring.messages.basename").split("\\s*,\\s*"));
        ms.setDefaultEncoding(props.getProperty("spring.messages.encoding"));
        ms.setFallbackToSystemLocale(Boolean.parseBoolean(props.getProperty("spring.messages.fallback-to-system-locale")));
        ms.setUseCodeAsDefaultMessage(Boolean.parseBoolean(props.getProperty("spring.messages.use-code-as-default-message")));
        ms.setAlwaysUseMessageFormat(Boolean.parseBoolean(props.getProperty("spring.messages.always-use-message-format")));
        return ms;
    }

    public static Messages messages() {
        return new Messages(messageSource(), IgrpLocale.PT);
    }

    public static LocalValidatorFactoryBean validator() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.setValidationMessageSource(messageSource());
        validator.afterPropertiesSet();
        return validator;
    }
}
