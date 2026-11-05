package cv.igrp.platform.access_management.shared.i18n;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.LocaleResolver;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/** BE-2 (FR-3) + startup wiring of {@link I18nConfig} (FR-20). */
class I18nPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(I18nConfig.class);

    @AfterEach
    void resetDefaultLocale() {
        LocaleContextHolder.setDefaultLocale(null);
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void contextFailsWithUnsupportedModuleDefault() {
        runner.withPropertyValues("igrp.i18n.default-locale=de")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure()).rootCause()
                            .hasMessageContaining("igrp.i18n.default-locale='de'");
                });
    }

    @Test
    void contextFailsWithUnsupportedPlatformDefault() {
        runner.withPropertyValues("igrp.i18n.platform-default-locale=es")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void contextStartsWithSupportedValuesAndBindsPlatformDefault() {
        runner.withPropertyValues("igrp.i18n.default-locale=en", "igrp.i18n.platform-default-locale=pt")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    I18nProperties props = ctx.getBean(I18nProperties.class);
                    assertThat(props.moduleDefault()).isEqualTo(IgrpLocale.EN);
                    assertThat(props.platformDefault()).isEqualTo(IgrpLocale.PT);
                    assertThat(ctx.getBean(DispatcherServlet.LOCALE_RESOLVER_BEAN_NAME, LocaleResolver.class))
                            .isInstanceOf(IgrpLocaleResolver.class);
                    assertThat(ctx).hasSingleBean(Messages.class);
                    // Outside a request the platform default is used, never the JVM locale.
                    LocaleContextHolder.resetLocaleContext();
                    assertThat(LocaleContextHolder.getLocale()).isEqualTo(Locale.forLanguageTag("pt"));
                });
    }

    @Test
    void regionalValuesAreAccepted() {
        runner.withPropertyValues("igrp.i18n.default-locale=en-GB", "igrp.i18n.platform-default-locale=pt_CV")
                .run(ctx -> assertThat(ctx.getBean(I18nProperties.class).platformDefault()).isEqualTo(IgrpLocale.PT));
    }

    @Test
    void defaultsMatchTheResolvedOpenQuestions() {
        I18nProperties props = new I18nProperties();
        assertThat(props.moduleDefault()).isEqualTo(IgrpLocale.EN);
        assertThat(props.platformDefault()).isEqualTo(IgrpLocale.PT);
        assertThat(I18nTestSupport.applicationProperties())
                .containsEntry("igrp.i18n.default-locale", "en")
                .containsEntry("igrp.i18n.platform-default-locale", "${IGRP_I18N_PLATFORM_DEFAULT_LOCALE:pt}")
                .containsEntry("spring.messages.fallback-to-system-locale", "false")
                .containsEntry("spring.messages.use-code-as-default-message", "false")
                .containsEntry("spring.messages.always-use-message-format", "true");
    }
}
