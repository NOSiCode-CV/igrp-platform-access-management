package cv.igrp.platform.access_management.shared.security;

import org.springframework.context.annotation.Primary;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;

/**
 * Custom converter to defensively extract and normalize OIDC claims (CNI, CMD, etc.)
 * mapping them into a canonical UserProfile, following iGRP global skills.
 */
@Component
@Primary
public class IgrpJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final org.slf4j.Logger log = 
        org.slf4j.LoggerFactory.getLogger(IgrpJwtAuthenticationConverter.class);

    private final JwtAuthenticationConverter defaultConverter = new JwtAuthenticationConverter();

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        AbstractAuthenticationToken defaultToken = defaultConverter.convert(jwt);
        Collection<GrantedAuthority> authorities = new java.util.ArrayList<>(defaultToken.getAuthorities());
        Map<String, Object> c = jwt.getClaims();

        // Map WSO2 roles from token to authorities
        java.util.List<String> roles = optArray(c, "roles");
        if (roles.isEmpty()) {
            roles = optArray(c, "groups");
        }
        for (String role : roles) {
            authorities.add(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role));
        }

        // Defensive Extraction using Helpers
        String sub = req(c, "sub");
        String iss = opt(c, "iss");
        String name = coalesce(opt(c, "name"), join(opt(c, "given_name"), opt(c, "family_name")));
        String email = normalizeEmail(coalesce(opt(c, "email"), opt(c, "emaill"), opt(c, "preferred_username")));
        String phone = normalizePhone(opt(c, "phone_number"));
        String nic = normalizeNic(coalesce(opt(c, "NIC"), opt(c, "nic"), opt(c, "national_id")));
        
        String acr = opt(c, "acr");
        String method = detectAuthMethod(acr);

        // Validation of critical business rules
        if ("cni".equals(method) && nic == null) {
            // Autentika CNI: sub contains the NIC directly — no separate NIC claim
            nic = normalizeNic(sub);
            log.debug("[JWT] CNI login: using sub as NIC fallback: {}", nic);
        }

        // Build canonical profile with type safety
        UserProfile profile = new UserProfile(
            sub, iss, nullSafe(name), nullSafe(email), nullSafe(phone), nullSafe(nic), method, java.util.List.of()
        );

        // Context mapping: convert the generic Resource Server Jwt into a recognized OidcIdToken
        org.springframework.security.oauth2.core.oidc.OidcIdToken idToken = 
            new org.springframework.security.oauth2.core.oidc.OidcIdToken(
                jwt.getTokenValue(), jwt.getIssuedAt(), jwt.getExpiresAt(), jwt.getClaims());

        // Create the native Spring Security OidcUser representation containing the profile
        IgrpOidcUser oidcUser = new IgrpOidcUser(authorities, idToken, profile);

        return new OidcContextAuthenticationToken(oidcUser, jwt, authorities);
    }

    // --- Helpers based on CLAIMS_MAPPING.md Skills ---

    private static String req(Map<String,Object> c, String k) {
        var v = c.get(k);
        if (v == null || String.valueOf(v).isBlank()) {
            throw new OAuth2AuthenticationException(new OAuth2Error("missing_claim"), "Mandatory claim is missing: " + k);
        }
        return String.valueOf(v).trim();
    }
  
    private static String opt(Map<String,Object> c, String k) {
        var v = c.get(k); 
        return v == null ? null : String.valueOf(v).trim();
    }
    
    private static java.util.List<String> optArray(Map<String,Object> c, String k) {
        var v = c.get(k);
        if (v instanceof java.util.Collection<?> col) {
            return col.stream().map(String::valueOf).toList();
        }
        return java.util.List.of();
    }
    
    private static String coalesce(String... vals) {
        for (String v : vals) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
    
    private static String join(String a, String b) {
        if ((a == null || a.isBlank()) && (b == null || b.isBlank())) return null;
        return (nullSafe(a) + " " + nullSafe(b)).trim().replaceAll("\\s+", " ");
    }
    
    private static String normalizeEmail(String email) {
        if (email == null) return null; 
        var e = email.trim().toLowerCase();
        return e.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") ? e : null;
    }
    
    private static String normalizePhone(String phone) {
        if (phone == null) return null; 
        var digits = phone.replaceAll("[^\\d+]", "");
        return digits.startsWith("+") ? digits : digits;
    }
    
    private static String normalizeNic(String nic) {
        if (nic == null) return null; 
        return nic.replaceAll("[\\s-]", "").toUpperCase();
    }
    
    private static String detectAuthMethod(String acr) {
        if (acr == null || acr.isBlank()) return "pwd";
        var lower = acr.toLowerCase();
        if (lower.contains("cni")) return "cni";
        if (lower.contains("cmdcv")) return "cmdcv";
        return "pwd";
    }
    
    private static String nullSafe(String s) { 
        return s == null ? "" : s; 
    }
}
