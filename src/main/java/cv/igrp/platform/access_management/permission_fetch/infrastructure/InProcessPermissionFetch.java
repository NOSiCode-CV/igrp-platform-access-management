package cv.igrp.platform.access_management.permission_fetch.infrastructure;

import cv.igrp.platform.access_management.permission_fetch.application.dto.PermissionSetDTO;
import cv.igrp.platform.access_management.permission_fetch.domain.service.PermissionFetchService;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * AM's answer to its own route filter's permission fetch, in-process (#36).
 *
 * <p>The starter's lookup reads the caller's set from Redis and, on a miss,
 * calls {@code GET /api/users/me/permissions} on AM with a {@link RestClient}.
 * In AM that call would go out and back into AM. This is the client AM gives
 * the lookup instead: its interceptor answers the call with
 * {@link PermissionFetchService#fetch(org.springframework.security.oauth2.jwt.Jwt, String)}
 * and never touches the network. The status codes are those of the HTTP
 * endpoint, so the starter's lookup decides exactly as it would against AM
 * over HTTP (401: no longer authorized; anything else: unavailable).
 *
 * <p>Interim, until the starter can be given the set directly (a
 * {@code PermissionSource}, asked of the starter's maintainer).
 */
public final class InProcessPermissionFetch {

    private static final Logger LOGGER = LoggerFactory.getLogger(InProcessPermissionFetch.class);

    private static final String BEARER = "Bearer ";

    /** The set is plain lists and a map: no application-specific mapping needed. */
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ObjectProvider<PermissionFetchService> fetches;
    private final ObjectProvider<JwtDecoder> decoders;

    public InProcessPermissionFetch(ObjectProvider<PermissionFetchService> fetches,
                                    ObjectProvider<JwtDecoder> decoders) {
        this.fetches = fetches;
        this.decoders = decoders;
    }

    /** The client for the starter's lookup: every call is answered here. */
    public RestClient client() {
        return RestClient.builder()
                .baseUrl("http://access-management.in-process")
                .requestInterceptor((request, body, execution) -> answer(request))
                .build();
    }

    ClientHttpResponse answer(HttpRequest request) {
        String applicationCode = UriComponentsBuilder.fromUri(request.getURI()).build()
                .getQueryParams().getFirst("applicationCode");
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        // The lookup makes one call only, the permission fetch: no path to tell apart.
        if (applicationCode == null) {
            return Response.status(HttpStatus.NOT_FOUND);
        }
        if (authorization == null || !authorization.startsWith(BEARER)) {
            return Response.status(HttpStatus.UNAUTHORIZED);
        }
        try {
            PermissionSetDTO set = fetches.getObject()
                    .fetch(decoders.getObject().decode(authorization.substring(BEARER.length())), applicationCode);
            return Response.json(JSON.writeValueAsBytes(set));
        } catch (JwtException ex) {
            return Response.status(HttpStatus.UNAUTHORIZED);
        } catch (IgrpResponseStatusException ex) {
            return Response.status(ex.getStatusCode());
        } catch (RuntimeException ex) {
            LOGGER.error("[Route Filter] In-process permission fetch of application '{}' failed", applicationCode, ex);
            return Response.status(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /** A buffered response; nothing to release. */
    private static final class Response implements ClientHttpResponse {

        private final HttpStatusCode status;
        private final HttpHeaders headers;
        private final byte[] body;

        private Response(HttpStatusCode status, HttpHeaders headers, byte[] body) {
            this.status = status;
            this.headers = headers;
            this.body = body;
        }

        static Response status(HttpStatusCode status) {
            return new Response(status, new HttpHeaders(), new byte[0]);
        }

        static Response json(byte[] body) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            return new Response(HttpStatus.OK, headers, body);
        }

        @Override
        public HttpStatusCode getStatusCode() {
            return status;
        }

        @Override
        public String getStatusText() {
            HttpStatus known = HttpStatus.resolve(status.value());
            return known == null ? "" : known.getReasonPhrase();
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public void close() {
            // Buffered: nothing to close.
        }

        @Override
        public String toString() {
            return status + " " + new String(body, StandardCharsets.UTF_8);
        }
    }
}
