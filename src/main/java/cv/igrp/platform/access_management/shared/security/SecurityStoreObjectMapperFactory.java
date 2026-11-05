package cv.igrp.platform.access_management.shared.security;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationServerJackson2Module;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Single definition of the {@link ObjectMapper} used to persist Spring Security
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
 * <p><b>Deliberately not a bean.</b> Publishing an {@link ObjectMapper} bean
 * would satisfy the {@code @ConditionalOnMissingBean(ObjectMapper.class)} guard
 * in Spring Boot's {@code JacksonAutoConfiguration}, which would silently
 * replace the mapper used for HTTP message conversion with this one — a mapper
 * configured with class-name default typing and an allowlist, which is correct
 * for a trusted store and wrong for request bodies. A static factory gives one
 * definition without that hazard.
 *
 * <p>The mapper relies on {@link SecurityJackson2Modules}, which enables
 * class-name default typing restricted to an allowlist. Anything outside the
 * allowlist fails to deserialize with {@code IllegalArgumentException: ... is
 * not in the allowlist}, so every type reachable from a stored object has to be
 * admitted explicitly via a Mixin. {@code SessionSerializationSpikeTest} and
 * {@code SessionStoreConversionTest} are the regression guards.
 */
public final class SecurityStoreObjectMapperFactory {

    private SecurityStoreObjectMapperFactory() {
    }

    /**
     * Build a mapper for a trusted security store. A new instance per call;
     * callers are expected to hold on to it.
     */
    public static ObjectMapper create(ClassLoader classLoader) {
        ObjectMapper mapper = new ObjectMapper();
        List<Module> securityModules = SecurityJackson2Modules.getModules(classLoader);
        mapper.registerModules(securityModules);
        mapper.registerModule(new OAuth2AuthorizationServerJackson2Module());

        // The empty JDK collection sentinels, which turn up whenever a
        // principal's authorities, scopes or claim maps are empty.
        mapper.addMixIn(Collections.emptySet().getClass(), AllowlistedClassMixin.class);
        mapper.addMixIn(Collections.emptyList().getClass(), AllowlistedClassMixin.class);
        mapper.addMixIn(Collections.emptyMap().getClass(), AllowlistedClassMixin.class);
        mapper.addMixIn(Collections.emptySortedSet().getClass(), AllowlistedClassMixin.class);
        mapper.addMixIn(Collections.emptySortedMap().getClass(), AllowlistedClassMixin.class);

        // The List.of()/Set.of()/Map.of() family. Distinct classes from the
        // Collections.empty* sentinels above and NOT covered by them:
        //   List.of()        -> ImmutableCollections$ListN
        //   List.of(a[,b])   -> ImmutableCollections$List12
        // IgrpJwtAuthenticationConverter builds a UserProfile with List.of(),
        // so without these a principal carrying one fails to deserialize.
        mapper.addMixIn(List.of().getClass(), AllowlistedClassMixin.class);
        mapper.addMixIn(List.of("a").getClass(), AllowlistedClassMixin.class);
        mapper.addMixIn(Set.of().getClass(), AllowlistedClassMixin.class);
        mapper.addMixIn(Set.of("a").getClass(), AllowlistedClassMixin.class);
        mapper.addMixIn(Map.of().getClass(), AllowlistedClassMixin.class);
        mapper.addMixIn(Map.of("a", "b").getClass(), AllowlistedClassMixin.class);

        // The iGRP custom principal types.
        mapper.addMixIn(IgrpOidcUser.class, AllowlistedClassMixin.class);
        mapper.addMixIn(OidcContextAuthenticationToken.class, AllowlistedClassMixin.class);
        mapper.addMixIn(UserProfile.class, AllowlistedClassMixin.class);

        return mapper;
    }

    /**
     * Marker Mixin that widens the {@link SecurityJackson2Modules} allowlist.
     * The annotations mirror those Spring Security applies to its own
     * serializable types so class-name default typing round-trips.
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
