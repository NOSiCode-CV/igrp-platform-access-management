package cv.igrp.platform.access_management.oauth_server.infrastructure.idp;

import cv.igrp.framework.auth.core.adapter.IAdapter;
import cv.igrp.framework.auth.core.exception.IAMException;
import cv.igrp.framework.auth.core.model.ClientRegistration;
import cv.igrp.framework.auth.core.model.IssuedClient;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** v3-17 / v3-18: AM's only door to the IdP for service clients, and its credential cache. */
class FederatedClientsTest {

    private static final Instant NOW = Instant.parse("2026-11-11T12:00:00Z");

    private final IAdapter adapter = mock(IAdapter.class);
    private final AtomicReference<Instant> now = new AtomicReference<>(NOW);
    private final FederatedClients clients = new FederatedClients(adapter, "keycloak", new Clock() {
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    });

    @Test
    @DisplayName("Only a client whose sole grant is client_credentials is a service client")
    void serviceClient() {
        assertTrue(FederatedClients.isServiceClient(Set.of("client_credentials")));
        assertFalse(FederatedClients.isServiceClient(Set.of("client_credentials", "refresh_token")));
        assertFalse(FederatedClients.isServiceClient(Set.of("authorization_code")));
        assertFalse(FederatedClients.isServiceClient(Set.of()));
        assertFalse(FederatedClients.isServiceClient(null));
        assertEquals("keycloak", clients.idp());
    }

    @Test
    @DisplayName("Create, update, rotate and delete go to the adapter as client_credentials clients")
    void delegates() throws IAMException {
        IssuedClient issued = new IssuedClient("svc", "s3cret", null);
        when(adapter.createClient(any())).thenReturn(issued);
        when(adapter.rotateClientSecret("svc")).thenReturn(issued);

        assertSame(issued, clients.create("svc", "Svc"));
        clients.update("svc", "Svc 2");
        assertSame(issued, clients.rotateSecret("svc"));
        clients.delete("svc");

        ArgumentCaptor<ClientRegistration> registration = ArgumentCaptor.forClass(ClientRegistration.class);
        verify(adapter).createClient(registration.capture());
        assertEquals(new ClientRegistration("svc", "Svc", Set.of("client_credentials"), List.of()), registration.getValue());
        verify(adapter).updateClient("svc", new ClientRegistration("svc", "Svc 2", Set.of("client_credentials"), List.of()));
        verify(adapter).deleteClient("svc");
    }

    @Test
    @DisplayName("An IdP failure becomes 502 Bad Gateway with the IdP's message")
    void idpFailures() throws IAMException {
        IAMException down = new IAMException("Keycloak said no");
        when(adapter.createClient(any())).thenThrow(down);
        when(adapter.rotateClientSecret(anyString())).thenThrow(down);
        doThrow(down).when(adapter).updateClient(anyString(), any());
        doThrow(down).when(adapter).deleteClient(anyString());

        for (Executable call : List.<Executable>of(() -> clients.create("svc", "Svc"),
                () -> clients.rotateSecret("svc"), () -> clients.update("svc", "Svc"), () -> clients.delete("svc"))) {
            IgrpResponseStatusException failure = assertThrows(IgrpResponseStatusException.class, call);
            assertEquals(502, failure.getStatusCode().value());
            assertEquals("Keycloak said no", failure.getBody().getDetail());
            assertSame(down, failure.getCause());
        }
    }

    @Test
    @DisplayName("Accepted credentials are remembered until the IdP token's exp, then asked again")
    void cachesUntilExp() throws IAMException {
        when(adapter.authenticateClient("svc", "good")).thenReturn(Optional.of(NOW.plusSeconds(300)));

        assertTrue(clients.authenticate("svc", "good"));
        assertTrue(clients.authenticate("svc", "good"));
        verify(adapter, times(1)).authenticateClient("svc", "good");

        now.set(NOW.plusSeconds(300));
        assertTrue(clients.authenticate("svc", "good"));
        verify(adapter, times(2)).authenticateClient("svc", "good");
    }

    @Test
    @DisplayName("A wrong secret never matches a remembered one: it always goes to the IdP")
    void wrongSecretAlwaysAsksTheIdp() throws IAMException {
        when(adapter.authenticateClient("svc", "good")).thenReturn(Optional.of(NOW.plusSeconds(300)));
        when(adapter.authenticateClient("svc", "bad")).thenReturn(Optional.empty());

        assertTrue(clients.authenticate("svc", "good"));
        assertFalse(clients.authenticate("svc", "bad"));
        assertFalse(clients.authenticate("svc", "bad"));
        verify(adapter, times(2)).authenticateClient("svc", "bad");
    }

    @Test
    @DisplayName("Rotating or deleting a client forgets its remembered credentials")
    void rotateAndDeleteForget() throws IAMException {
        when(adapter.authenticateClient(eq("svc"), anyString())).thenReturn(Optional.of(NOW.plusSeconds(300)));
        when(adapter.rotateClientSecret("svc")).thenReturn(new IssuedClient("svc", "new", null));

        clients.authenticate("svc", "old");
        clients.rotateSecret("svc");
        clients.authenticate("svc", "old");
        verify(adapter, times(2)).authenticateClient("svc", "old");

        clients.delete("svc");
        clients.authenticate("svc", "old");
        verify(adapter, times(3)).authenticateClient("svc", "old");
    }

    @Test
    @DisplayName("Other clients' remembered credentials survive a rotation")
    void forgetsOnlyThatClient() throws IAMException {
        when(adapter.authenticateClient(anyString(), anyString())).thenReturn(Optional.of(NOW.plusSeconds(300)));
        when(adapter.rotateClientSecret("svc")).thenReturn(new IssuedClient("svc", "new", null));

        clients.authenticate("svc-2", "x");
        clients.rotateSecret("svc");
        clients.authenticate("svc-2", "x");
        verify(adapter, times(1)).authenticateClient("svc-2", "x");
    }

    @Test
    @DisplayName("An unreachable IdP is an error, not a refusal")
    void idpUnreachable() throws IAMException {
        when(adapter.authenticateClient("svc", "good")).thenThrow(new IAMException("down"));
        assertThrows(IAMException.class, () -> clients.authenticate("svc", "good"));
    }
}
