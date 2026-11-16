package cv.igrp.platform.access_management.shared.config;

import cv.igrp.framework.auth.core.field.FieldPermissionModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.Serial;

/**
 * Stopgap for the 0.3 starter's {@code FieldPermissionModule} (#36). Its
 * serializer modifier returns an immutable property list, which Jackson then
 * changes while building some serializers ({@code ProblemDetail} among them),
 * so every iGRP error response failed to serialise (500 or a bare 400 instead
 * of the problem detail with its code).
 *
 * <p>AM has no {@code @FieldPermission} fields, so it registers the module
 * without its modifier; the starter's own bean is
 * {@code @ConditionalOnMissingBean} and steps aside. Remove this class once the
 * starter returns a mutable list.
 */
@Configuration
public class FieldPermissionStopgapConfig {

    @Bean
    public FieldPermissionModule amFieldPermissionModule() {
        return new WithoutFieldFiltering();
    }

    /** The module with no serializer modifier: AM's responses have no guarded fields. */
    static final class WithoutFieldFiltering extends FieldPermissionModule {

        @Serial
        private static final long serialVersionUID = 1L;

        WithoutFieldFiltering() {
            setSerializerModifier(null);
        }
    }
}
