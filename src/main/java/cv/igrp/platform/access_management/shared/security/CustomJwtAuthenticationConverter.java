package cv.igrp.platform.access_management.shared.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Custom JWT authentication converter that maps Keycloak resource access roles
 * to Spring Security authorities, including special session management permissions.
 */
public class CustomJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final Logger log = LoggerFactory.getLogger(CustomJwtAuthenticationConverter.class);
    private final JwtAuthenticationConverter defaultConverter = new JwtAuthenticationConverter();

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        log.info(" CustomJwtAuthenticationConverter.convert() called for user: {}", jwt.getSubject());
        
        // Get default authorities from scope/roles
        JwtAuthenticationToken defaultToken = (JwtAuthenticationToken) defaultConverter.convert(jwt);
        Collection<GrantedAuthority> authorities = new ArrayList<>(defaultToken.getAuthorities());

        // Map resource access roles to authorities
        Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
        if (resourceAccess != null) {
            Map<String, Object> accessManagement = (Map<String, Object>) resourceAccess.get("access-management");
            if (accessManagement != null) {
                List<String> roles = (List<String>) accessManagement.get("roles");
                if (roles != null) {
                    log.info(" Found roles in token: {}", roles);
                    for (String role : roles) {
                        // Add role as authority
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                        
                        // Map specific roles to session management permissions
                        if (role.contains("superadmin") || role.contains("admin")) {
                            log.info(" Adding SESSION_MANAGEMENT authority for role: {}", role);
                            authorities.add(new SimpleGrantedAuthority("SESSION_MANAGEMENT"));
                        }
                    }
                }
            }
        }

        log.info(" Final authorities: {}", authorities);
        return new JwtAuthenticationToken(jwt, authorities, jwt.getClaimAsString("sub"));
    }
}
