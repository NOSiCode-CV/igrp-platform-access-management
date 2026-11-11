package cv.igrp.platform.access_management.oauth_server.infrastructure.idp;

import cv.igrp.framework.auth.core.adapter.IAdapter;
import cv.igrp.framework.auth.core.exception.IAMException;
import cv.igrp.framework.auth.core.model.ClientRegistration;
import cv.igrp.framework.auth.core.model.IssuedClient;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service clients federated in the IdP (v3-17, v3-18): the only place AM talks to the
 * IdP's adapter. A service client is one whose only grant is {@code client_credentials};
 * it lives in the IdP and AM never stores its secret. Login clients stay AM-local.
 *
 * <p>Credentials the IdP accepted are remembered until the IdP token's {@code exp},
 * keyed by client id and a SHA-256 of the secret, in memory only: a wrong secret never
 * matches an entry and always goes to the IdP. Rotating or deleting a client forgets it.
 */
@Component
public class FederatedClients {

    static final String CLIENT_CREDENTIALS = "client_credentials";
    private static final int MAX_CACHED = 10_000;

    private final IAdapter adapter;
    private final String idp;
    private final Clock clock;
    private final Map<String, Instant> accepted = new ConcurrentHashMap<>();

    @Autowired
    public FederatedClients(IAdapter adapter, @Value("${igrp.oauth.service-clients.idp:keycloak}") String idp) {
        this(adapter, idp, Clock.systemUTC());
    }

    FederatedClients(IAdapter adapter, String idp, Clock clock) {
        this.adapter = adapter;
        this.idp = idp;
        this.clock = clock;
    }

    /** The IdP new service clients are created in, stored on the client row. */
    public String idp() {
        return idp;
    }

    /** A client whose only grant is {@code client_credentials} is a service client, federated in the IdP. */
    public static boolean isServiceClient(Set<String> grantTypes) {
        return grantTypes != null && grantTypes.equals(Set.of(CLIENT_CREDENTIALS));
    }

    public IssuedClient create(String clientId, String clientName) {
        try {
            return adapter.createClient(registration(clientId, clientName));
        } catch (IAMException ex) {
            throw idpFailure("Could not create the client in the IdP", ex);
        }
    }

    public IssuedClient rotateSecret(String clientId) {
        forget(clientId);
        try {
            return adapter.rotateClientSecret(clientId);
        } catch (IAMException ex) {
            throw idpFailure("Could not rotate the client secret in the IdP", ex);
        }
    }

    public void update(String clientId, String clientName) {
        try {
            adapter.updateClient(clientId, registration(clientId, clientName));
        } catch (IAMException ex) {
            throw idpFailure("Could not update the client in the IdP", ex);
        }
    }

    public void delete(String clientId) {
        forget(clientId);
        try {
            adapter.deleteClient(clientId);
        } catch (IAMException ex) {
            throw idpFailure("Could not delete the client in the IdP", ex);
        }
    }

    /**
     * Whether the IdP accepts these credentials. Answered from memory while a previous
     * acceptance of the very same secret is unexpired.
     *
     * @throws IAMException if the IdP could not answer
     */
    public boolean authenticate(String clientId, String clientSecret) throws IAMException {
        String key = clientId + ':' + sha256(clientSecret);
        Instant now = clock.instant();
        Instant until = accepted.get(key);
        if (until != null && until.isAfter(now)) {
            return true;
        }
        var expiry = adapter.authenticateClient(clientId, clientSecret);
        if (expiry.isEmpty()) {
            accepted.remove(key);
            return false;
        }
        if (accepted.size() >= MAX_CACHED) {
            accepted.values().removeIf(exp -> !exp.isAfter(now));
        }
        if (accepted.size() < MAX_CACHED) {
            accepted.put(key, expiry.get());
        }
        return true;
    }

    private void forget(String clientId) {
        String prefix = clientId + ':';
        accepted.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private static ClientRegistration registration(String clientId, String clientName) {
        return new ClientRegistration(clientId, clientName, Set.of(CLIENT_CREDENTIALS), List.of());
    }

    private static IgrpResponseStatusException idpFailure(String title, IAMException cause) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, cause.getMessage());
        problem.setTitle(title);
        return new IgrpResponseStatusException(HttpStatus.BAD_GATEWAY, problem, cause);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
