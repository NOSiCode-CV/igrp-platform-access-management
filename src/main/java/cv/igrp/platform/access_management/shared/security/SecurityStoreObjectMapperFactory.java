package cv.igrp.platform.access_management.shared.security;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.springframework.security.jackson.SecurityJacksonModules;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Single definition of the {@link JsonMapper} used to persist Spring Security
 * objects outside the JVM. Two stores need it and must not drift apart:
 *
 * <ul>
 *   <li>the OAuth2 authorization store — {@code AuthorizationServerConfig}
 *       hands it to {@code JdbcOAuth2AuthorizationService};</li>
 *   <li>the shared servlet session — {@code SessionStoreConfig} wraps it in the
 *       {@code springSessionConversionService} so a login survives landing on a
 *       different replica (R3.2).</li>
 * </ul>
 *
 * <p><b>Deliberately not a bean.</b> Publishing a {@link JsonMapper} bean would
 * replace the one Spring Boot auto-configures for HTTP message conversion with
 * this one, configured with class-name default typing and an allowlist, which
 * is correct for a trusted store and wrong for request bodies. A static factory
 * gives one definition without that hazard.
 *
 * <p>The mapper relies on {@link SecurityJacksonModules}, which enables
 * class-name default typing restricted to an allowlist. Anything outside the
 * allowlist fails to deserialize, so every type reachable from a stored object
 * has to be admitted explicitly, both in the type validator and via a Mixin.
 * {@code SessionSerializationSpikeTest}, {@code SessionStoreConversionTest} and
 * {@code AuthorizationStoreRoundTripIT} are the regression guards.
 */
public final class SecurityStoreObjectMapperFactory {

    // The empty JDK collection sentinels, which turn up whenever a principal's
    // authorities, scopes or claim maps are empty, and the List.of()/Set.of()/
    // Map.of() family, which are distinct classes:
    //   List.of()        -> ImmutableCollections$ListN
    //   List.of(a[,b])   -> ImmutableCollections$List12
    // IgrpJwtAuthenticationConverter builds a UserProfile with List.of(), so
    // without these a principal carrying one fails to deserialize.
    private static final List<Class<?>> ALLOWLISTED_TYPES = List.of(
            Collections.emptySet().getClass(),
            Collections.emptyList().getClass(),
            Collections.emptyMap().getClass(),
            Collections.emptySortedSet().getClass(),
            Collections.emptySortedMap().getClass(),
            List.of().getClass(),
            List.of("a").getClass(),
            Set.of().getClass(),
            Set.of("a").getClass(),
            Map.of().getClass(),
            Map.of("a", "b").getClass(),
            // The iGRP custom principal types.
            IgrpOidcUser.class,
            OidcContextAuthenticationToken.class,
            UserProfile.class);

    private SecurityStoreObjectMapperFactory() {
    }

    /**
     * Build a mapper for a trusted security store. A new instance per call;
     * callers are expected to hold on to it.
     */
    public static JsonMapper create(ClassLoader classLoader) {
        BasicPolymorphicTypeValidator.Builder allowlist = BasicPolymorphicTypeValidator.builder();
        JsonMapper.Builder builder = JsonMapper.builder();
        ALLOWLISTED_TYPES.forEach(type -> {
            allowlist.allowIfSubType(type);
            builder.addMixIn(type, AllowlistedClassMixin.class);
        });
        return builder
                .addModules(SecurityJacksonModules.getModules(classLoader, allowlist))
                .build();
    }

    /**
     * Marker Mixin that gives an allowlisted type the same treatment Spring
     * Security applies to its own serializable types, so class-name default
     * typing round-trips.
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
    @JsonAutoDetect(
            fieldVisibility = JsonAutoDetect.Visibility.ANY,
            getterVisibility = JsonAutoDetect.Visibility.NONE,
            isGetterVisibility = JsonAutoDetect.Visibility.NONE,
            creatorVisibility = JsonAutoDetect.Visibility.ANY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public abstract static class AllowlistedClassMixin {
    }
}
