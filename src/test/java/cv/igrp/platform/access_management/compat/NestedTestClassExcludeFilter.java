package cv.igrp.platform.access_management.compat;

import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;

/**
 * Keeps other tests' fixtures out of the compat suite's application context.
 *
 * <p>The application scans all of {@code cv.igrp}, and several unit tests
 * declare nested {@code @Configuration} / {@code @RestController} fixtures
 * (some with {@code @EnableWebMvc}). Spring Boot's own test filter only
 * recognises them when the enclosing class itself carries {@code @Test}
 * methods, which is not the case for {@code @Nested}-only containers, so they
 * would leak into the real application and break it. This filter excludes any
 * class nested in a {@code *Test}, {@code *Tests} or {@code *IT} class.
 */
public class NestedTestClassExcludeFilter extends TypeExcludeFilter {

    @Override
    public boolean match(MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory) {
        String className = metadataReader.getClassMetadata().getClassName();
        int nested = className.indexOf('$');
        if (nested < 0) {
            return false;
        }
        String outer = className.substring(0, nested);
        return outer.endsWith("Test") || outer.endsWith("Tests") || outer.endsWith("IT");
    }

    @Override
    public boolean equals(Object obj) {
        return obj != null && obj.getClass() == getClass();
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
