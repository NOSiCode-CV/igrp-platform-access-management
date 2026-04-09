package cv.igrp.platform.access_management.shared.infrastructure.service;

import cv.igrp.platform.access_management.shared.domain.audit.AuthAuditContext;
import cv.igrp.platform.access_management.shared.domain.audit.IdentifierType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class AuthAuditServiceTest {

    @Test
    @DisplayName("1. JWT with claims: amr=OpenIDConnectAuthenticator, acr=cmdcv")
    void testJwtWithCmd() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("amr", List.of("OpenIDConnectAuthenticator"));
        claims.put("acr", "cmdcv");
        claims.put("phone_number", "+2385162210");
        claims.put("sub", "19800408M003H");

        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .claims(c -> c.putAll(claims))
                .jti("test-session-cmd")
                .build();

        AuthAuditContext ctx = AuthAuditService.fromAutentikaJwt(jwt, null);

        assertThat(ctx.identifierType()).isEqualTo(IdentifierType.CMDCV);
        assertThat(ctx.identifierValue()).isEqualTo("+2385162210");
        
        String hashedValue = AuthAuditService.hash(ctx.identifierValue());
        assertThat(hashedValue).hasSize(64);
        assertNotEquals("+2385162210", hashedValue);
    }

    @Test
    @DisplayName("2. JWT with claims: amr=OpenIDConnectAuthenticator, acr=cni")
    void testJwtWithCni() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("amr", List.of("OpenIDConnectAuthenticator"));
        claims.put("acr", "cni");
        claims.put("sub", "19800408M003H");

        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .claims(c -> c.putAll(claims))
                .jti("test-session-cni")
                .build();

        AuthAuditContext ctx = AuthAuditService.fromAutentikaJwt(jwt, null);

        assertThat(ctx.identifierType()).isEqualTo(IdentifierType.CNI);
        assertThat(ctx.identifierValue()).isEqualTo("19800408M003H");
        
        String hashedValue = AuthAuditService.hash(ctx.identifierValue());
        assertThat(hashedValue).hasSize(64);
        assertNotEquals("19800408M003H", hashedValue);
    }

    @Test
    @DisplayName("3. JWT with amr=BasicAuthenticator, acr=pwd")
    void testJwtWithEmail() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("amr", List.of("BasicAuthenticator"));
        claims.put("acr", "pwd");
        claims.put("email", "marcelo.fernandes@nosi.cv");
        claims.put("sub", "marcelo.fernandes@nosi.cv");

        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .claims(c -> c.putAll(claims))
                .jti("test-session-ad")
                .build();

        AuthAuditContext ctx = AuthAuditService.fromAutentikaJwt(jwt, null);

        assertThat(ctx.identifierType()).isEqualTo(IdentifierType.EMAIL);
        assertThat(ctx.identifierValue()).isEqualTo("marcelo.fernandes@nosi.cv");
    }

    @Test
    @DisplayName("4. JWT with only: sub, jti")
    void testJwtWithUnknown() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "19800408M003H");

        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .claims(c -> c.putAll(claims))
                .jti("test-session-unknown")
                .build();

        AuthAuditContext ctx = AuthAuditService.fromAutentikaJwt(jwt, null);

        assertThat(ctx.identifierType()).isEqualTo(IdentifierType.UNKNOWN);
        assertThat(ctx.identifierValue()).isNull();
    }
}

