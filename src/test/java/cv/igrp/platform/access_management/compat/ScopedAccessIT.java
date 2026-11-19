package cv.igrp.platform.access_management.compat;

import cv.igrp.platform.access_management.app.specs.MenuSpecificationBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spring Boot 4 migration gate — department scoping is really applied.
 *
 * <p>{@code ScopeAspect} fills the {@code ScopeContext} that the {@code @Scoped}
 * menu specification reads (the caller and whether they are root), and it only
 * runs if AspectJ support is on the classpath. Row visibility itself is the
 * starter's scope filter (#25), tested in {@code AmScopesIT}.
 * Boot 3.5 brought that in through {@code spring-boot-starter-data-jpa}; Boot 4
 * does not, and needs {@code spring-boot-starter-aspectj}. Without the aspect
 * nobody counts as superadmin and the listing comes back without the data.
 */
class ScopedAccessIT extends AbstractCompatIT {

    @Autowired
    ApplicationContext context;
    @Autowired
    MenuSpecificationBuilder menuSpecificationBuilder;

    @Test
    @DisplayName("The @Scoped specification builder is advised by ScopeAspect")
    void aspectIsWoven() {
        assertTrue(AopUtils.isAopProxy(menuSpecificationBuilder),
                "MenuSpecificationBuilder is not proxied: ScopeAspect is not active");
    }

    @Test
    @DisplayName("A scoped listing returns the data the caller may see")
    void scopedListingReturnsData() {
        CompatSupport api = new CompatSupport(context);
        api.ensureBusinessData();

        HttpResponse<String> response = api.get("/api/departments", api.superadminToken());

        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.body().contains(CompatSupport.BUSINESS_DEPARTMENT), response.body());
    }
}
