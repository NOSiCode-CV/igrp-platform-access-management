package cv.igrp.platform.access_management.compat;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.filter.annotation.TypeExcludeFilters;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base for the Spring Boot 4 migration gate: boots the real application
 * (production profile, every filter chain and auto-configuration) on
 * {@link CompatEnvironment#PORT} against containerised Postgres and Redis.
 *
 * <p>All subclasses share one application context: they must not add
 * {@code @MockitoBean}s or extra properties, or Spring starts a second one.
 */
@Tag("compat")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TypeExcludeFilters(NestedTestClassExcludeFilter.class)
@ActiveProfiles(CompatEnvironment.PROFILE)
public abstract class AbstractCompatIT {

    @DynamicPropertySource
    static void compatProperties(DynamicPropertyRegistry registry) {
        CompatEnvironment.register(registry);
    }
}
