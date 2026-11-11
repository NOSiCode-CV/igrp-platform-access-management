package cv.igrp.platform.access_management.oauth_server.infrastructure.idp;

import cv.igrp.framework.auth.core.exception.IAMException;
import cv.igrp.platform.access_management.oauth_server.infrastructure.security.IgrpRegisteredClientRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** v3-18: the token endpoint asks the IdP for federated clients and leaves every other client alone. */
class FederatedClientAuthenticationProviderTest {

    private final RegisteredClientRepository repository = mock(RegisteredClientRepository.class);
    private final FederatedClients federated = mock(FederatedClients.class);
    private final FederatedClientAuthenticationProvider provider =
            new FederatedClientAuthenticationProvider(repository, federated);

    private static RegisteredClient client(String clientId, boolean isFederated) {
        return RegisteredClient.withId("id-" + clientId).clientId(clientId)
                .clientSecret(isFederated ? null : "{bcrypt}hash")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .clientSettings(ClientSettings.builder()
                        .setting(IgrpRegisteredClientRepository.FEDERATED_IDP_SETTING, isFederated).build())
                .build();
    }

    private static OAuth2ClientAuthenticationToken presented(String clientId, ClientAuthenticationMethod method, Object secret) {
        return new OAuth2ClientAuthenticationToken(clientId, method, secret, null);
    }

    @Test
    @DisplayName("Accepted by the IdP: an authenticated client token for the registered client")
    void accepted() throws IAMException {
        RegisteredClient svc = client("svc", true);
        when(repository.findByClientId("svc")).thenReturn(svc);
        when(federated.authenticate("svc", "good")).thenReturn(true);

        var result = (OAuth2ClientAuthenticationToken) provider.authenticate(
                presented("svc", ClientAuthenticationMethod.CLIENT_SECRET_BASIC, "good"));

        assertTrue(result.isAuthenticated());
        assertSame(svc, result.getRegisteredClient());
        assertEquals(ClientAuthenticationMethod.CLIENT_SECRET_BASIC, result.getClientAuthenticationMethod());
        assertNull(result.getCredentials(), "the secret must not travel into the stored authorization");
    }

    @Test
    @DisplayName("Refused by the IdP: invalid_client")
    void refused() throws IAMException {
        when(repository.findByClientId("svc")).thenReturn(client("svc", true));
        when(federated.authenticate("svc", "bad")).thenReturn(false);

        var failure = assertThrows(OAuth2AuthenticationException.class, () -> provider.authenticate(
                presented("svc", ClientAuthenticationMethod.CLIENT_SECRET_BASIC, "bad")));
        assertEquals(OAuth2ErrorCodes.INVALID_CLIENT, failure.getError().getErrorCode());
    }

    @Test
    @DisplayName("IdP unreachable: server_error, never a silent acceptance")
    void idpDown() throws IAMException {
        when(repository.findByClientId("svc")).thenReturn(client("svc", true));
        when(federated.authenticate("svc", "good")).thenThrow(new IAMException("down"));

        var failure = assertThrows(OAuth2AuthenticationException.class, () -> provider.authenticate(
                presented("svc", ClientAuthenticationMethod.CLIENT_SECRET_BASIC, "good")));
        assertEquals(OAuth2ErrorCodes.SERVER_ERROR, failure.getError().getErrorCode());
    }

    @Test
    @DisplayName("No secret, or a method the client is not registered for: invalid_client without calling the IdP")
    void badPresentation() {
        when(repository.findByClientId("svc")).thenReturn(client("svc", true));

        for (var token : new OAuth2ClientAuthenticationToken[]{
                presented("svc", ClientAuthenticationMethod.CLIENT_SECRET_BASIC, ""),
                presented("svc", ClientAuthenticationMethod.CLIENT_SECRET_BASIC, null),
                presented("svc", ClientAuthenticationMethod.CLIENT_SECRET_POST, "good")}) {
            var failure = assertThrows(OAuth2AuthenticationException.class, () -> provider.authenticate(token));
            assertEquals(OAuth2ErrorCodes.INVALID_CLIENT, failure.getError().getErrorCode());
        }
        verifyNoInteractions(federated);
    }

    @Test
    @DisplayName("AM-local and unknown clients, and non-secret methods, are left to the other providers")
    void notOurs() {
        when(repository.findByClientId("local")).thenReturn(client("local", false));

        assertNull(provider.authenticate(presented("local", ClientAuthenticationMethod.CLIENT_SECRET_BASIC, "x")));
        assertNull(provider.authenticate(presented("ghost", ClientAuthenticationMethod.CLIENT_SECRET_BASIC, "x")));
        assertNull(provider.authenticate(presented("svc", ClientAuthenticationMethod.NONE, null)));
        verifyNoInteractions(federated);
    }

    @Test
    @DisplayName("Supports client authentication tokens only")
    void supports() {
        assertTrue(provider.supports(OAuth2ClientAuthenticationToken.class));
        assertFalse(provider.supports(UsernamePasswordAuthenticationToken.class));
    }
}
