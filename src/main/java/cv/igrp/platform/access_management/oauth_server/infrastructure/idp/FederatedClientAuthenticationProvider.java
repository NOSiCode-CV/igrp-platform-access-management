package cv.igrp.platform.access_management.oauth_server.infrastructure.idp;

import cv.igrp.framework.auth.core.exception.IAMException;
import cv.igrp.platform.access_management.oauth_server.infrastructure.security.IgrpRegisteredClientRepository;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/**
 * Client authentication for service clients federated in the IdP (v3-18): AM holds no
 * secret for them, so the presented credentials go to the IdP through {@link FederatedClients}.
 * Runs before Spring Authorization Server's own secret check and ignores every other client.
 */
public class FederatedClientAuthenticationProvider implements AuthenticationProvider {

    private final RegisteredClientRepository clients;
    private final FederatedClients federatedClients;

    public FederatedClientAuthenticationProvider(RegisteredClientRepository clients, FederatedClients federatedClients) {
        this.clients = clients;
        this.federatedClients = federatedClients;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        OAuth2ClientAuthenticationToken token = (OAuth2ClientAuthenticationToken) authentication;
        ClientAuthenticationMethod method = token.getClientAuthenticationMethod();
        if (!ClientAuthenticationMethod.CLIENT_SECRET_BASIC.equals(method)
                && !ClientAuthenticationMethod.CLIENT_SECRET_POST.equals(method)) {
            return null;
        }
        Object principal = token.getPrincipal();
        if (principal == null) {
            return null;
        }
        String clientId = principal.toString();
        RegisteredClient client = clients.findByClientId(clientId);
        if (client == null || !Boolean.TRUE.equals(client.getClientSettings()
                .getSetting(IgrpRegisteredClientRepository.FEDERATED_IDP_SETTING))) {
            return null;
        }
        if (!client.getClientAuthenticationMethods().contains(method)
                || !(token.getCredentials() instanceof String secret) || secret.isEmpty()) {
            throw invalidClient("Client authentication failed");
        }
        boolean accepted;
        try {
            accepted = federatedClients.authenticate(clientId, secret);
        } catch (IAMException ex) {
            throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR,
                    "The identity provider could not check the credentials of client " + clientId, null), ex);
        }
        if (!accepted) {
            throw invalidClient("Client authentication failed");
        }
        // No credentials on the result: the authorization stored for the token must not carry the secret.
        return new OAuth2ClientAuthenticationToken(client, method, null);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2ClientAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private static OAuth2AuthenticationException invalidClient(String description) {
        return new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_CLIENT, description, null));
    }
}
