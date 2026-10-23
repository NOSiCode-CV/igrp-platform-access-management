package cv.igrp.platform.access_management.shared.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R3.2 §7 V8 — the resource-server chain must stay stateless.
 *
 * <p>The decision to put the servlet session in Postgres rests on this one fact:
 * API traffic creates no session, so the store only ever sees the browser
 * authorization flow — a few writes per login rather than per request. The whole
 * volume argument in {@code plans/shared-servlet-session/scope.md} §1.1 collapses
 * without it, and every API call would become a session write.
 *
 * <p>Asserted as source text rather than behaviour on purpose. A request-level
 * test would need the full application context and would still only cover the
 * routes it exercised; the policy is a single declaration, and what needs
 * guarding is that nobody removes or weakens it. If this test fails because the
 * configuration was legitimately restructured, re-measure the session write
 * volume before changing the assertion to match.
 */
class ResourceServerChainStatelessTest {

    private static final Path CONFIG = Path.of(
            "src/main/java/cv/igrp/platform/access_management/shared/security/OAuth2SecurityConfiguration.java");

    @Test
    @DisplayName("The API chain declares SessionCreationPolicy.STATELESS")
    void apiChainIsStateless() throws IOException {
        String source = Files.readString(CONFIG, StandardCharsets.UTF_8);

        assertTrue(source.contains("sessionCreationPolicy(SessionCreationPolicy.STATELESS)"),
                """
                OAuth2SecurityConfiguration no longer declares STATELESS session management.

                R3.2 stores the servlet session in Postgres on the assumption that API
                traffic never creates one. Dropping STATELESS turns every authenticated
                API request into a SPRING_SESSION write plus a LAST_ACCESS_TIME update.

                If this is intended, re-read plans/shared-servlet-session/scope.md Section 1.1
                and reconsider the store before updating this test.""");
    }
}
