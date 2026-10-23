package cv.igrp.platform.access_management.shared.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import cv.igrp.platform.access_management.shared.security.SecurityStoreObjectMapperFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.session.jdbc.PostgreSqlJdbcIndexedSessionRepositoryCustomizer;

import java.io.UncheckedIOException;

/**
 * R3.2 — the servlet session lives in Postgres so a login survives landing on a
 * different replica.
 *
 * <p>Fixes {@code authorization_request_not_found} ("Login could not be
 * completed"): Spring's default {@code HttpSessionOAuth2AuthorizationRequestRepository}
 * keeps the pending authorization request in the servlet session, so a browser
 * that starts at {@code /oauth2/authorize} on one pod and returns to
 * {@code /login/oauth2/code/external-idp} on another finds no saved request to
 * match its {@code state} against. A rolling deploy reproduces it on a single
 * replica too.
 *
 * <p>Postgres rather than Redis: it is already a hard dependency of login
 * ({@code t_user}, {@code t_user_session}), so the session store adds no new
 * failure mode, whereas Redis today backs only caches and degrades gracefully
 * ({@code CacheConfig.SafeCache}) — a session cannot degrade that way, because a
 * cache miss is only safe when the value is derivable. Write volume is a
 * non-issue: the resource-server chain is {@code STATELESS}
 * ({@code OAuth2SecurityConfiguration}), so API traffic touches no session and
 * only the browser authorization flow writes here.
 *
 * <p>Reasoning and the rejected alternatives: {@code plans/shared-servlet-session/scope.md}.
 *
 * <p>The tables are created by {@link DatabaseMigrationRunner}, not by Spring
 * Boot's session initializer — see {@code spring.session.jdbc.initialize-schema=never}.
 */
@Configuration
@ConditionalOnProperty(name = "spring.session.store-type", havingValue = "jdbc")
public class SessionStoreConfig {

    /**
     * Store session attributes as JSON using the same mapper as the OAuth2
     * authorization store, rather than Spring Session's default JDK
     * serialization.
     *
     * <p>JDK serialization does not work as the code stands: {@code IgrpOidcUser}
     * holds a {@code UserProfile} record that is not {@code Serializable}, so
     * writing the security context would fail with {@code NotSerializableException}.
     * Making it work would mean a second serialization contract to maintain
     * beside the JSON one that already exists, plus {@code serialVersionUID}
     * discipline on a type that is still changing. JSON also leaves sessions
     * readable with SQL, which is the point when debugging a login that failed
     * on one pod.
     *
     * <p>Bean name is fixed: {@code JdbcHttpSessionConfiguration} injects it by
     * the qualifier {@code springSessionConversionService}. Renaming it silently
     * falls back to JDK serialization.
     *
     * <p>Deserialization reads into {@link Object}, so it depends on the
     * class-name default typing {@code SecurityJackson2Modules} enables —
     * restricted to an allowlist, which is why every reachable type needs a
     * Mixin. {@code SessionStoreConversionTest} is the guard.
     */
    @Bean("springSessionConversionService")
    public ConversionService springSessionConversionService() {
        ObjectMapper mapper = SecurityStoreObjectMapperFactory.create(getClass().getClassLoader());
        GenericConversionService conversionService = new GenericConversionService();
        conversionService.addConverter(Object.class, byte[].class, serializer(mapper));
        conversionService.addConverter(byte[].class, Object.class, deserializer(mapper));
        return conversionService;
    }

    /**
     * Make the attribute write an upsert.
     *
     * <p>Spring Session's default {@code createSessionAttributeQuery} is a plain
     * {@code INSERT}. Spring Boot's auto-configuration does <b>not</b> register
     * this customizer — {@code JdbcSessionConfiguration} only sets the timeout,
     * table name and cleanup cron — so without it two concurrent requests on one
     * session that both see an attribute as new race to insert the same
     * {@code (SESSION_PRIMARY_ID, ATTRIBUTE_NAME)} and the loser fails on the
     * primary key. The OAuth2 authorize flow issues concurrent requests against
     * one session, and a duplicate-key failure there surfaces to the user as a
     * failed login. The Postgres customizer replaces the insert with
     * {@code ON CONFLICT ... DO UPDATE}.
     */
    @Bean
    public SessionRepositoryCustomizer<JdbcIndexedSessionRepository> postgresSessionRepositoryCustomizer() {
        return new PostgreSqlJdbcIndexedSessionRepositoryCustomizer();
    }

    private static Converter<Object, byte[]> serializer(ObjectMapper mapper) {
        return source -> {
            try {
                return mapper.writeValueAsBytes(source);
            } catch (JsonProcessingException e) {
                // Surfacing this is deliberate. Swallowing it would store a
                // half-written session and sign the user out on the next
                // request, with nothing in the logs pointing at the attribute
                // that could not be written.
                throw new UncheckedIOException(
                        "Could not serialize session attribute of type "
                                + source.getClass().getName()
                                + ". It is most likely missing from the Jackson allowlist in "
                                + "SecurityStoreObjectMapperFactory.", e);
            }
        };
    }

    private static Converter<byte[], Object> deserializer(ObjectMapper mapper) {
        return source -> {
            try {
                return mapper.readValue(source, Object.class);
            } catch (Exception e) {
                throw new UncheckedIOException(
                        "Could not deserialize session attribute. Either the stored format predates a "
                                + "change to the attribute's class, or its type is missing from the Jackson "
                                + "allowlist in SecurityStoreObjectMapperFactory.",
                        e instanceof java.io.IOException io ? io : new java.io.IOException(e));
            }
        };
    }
}
