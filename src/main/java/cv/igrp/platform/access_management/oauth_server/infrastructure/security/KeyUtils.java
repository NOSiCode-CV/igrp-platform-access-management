package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Loads RSA key pairs from PEM-formatted resources for use by the OAuth2
 * authorization server when signing issued JWT access tokens.
 *
 * <p>Source paths are resolved through Spring's {@link ResourceLoader}, so
 * {@code classpath:}, {@code file:}, and absolute paths are all accepted.
 */
@Component
public class KeyUtils {

    private final ResourceLoader resourceLoader;
    private final String publicKeyLocation;
    private final String privateKeyLocation;

    public KeyUtils(ResourceLoader resourceLoader,
                    @Value("${igrp.oauth.keys.public:classpath:keys/public.pem}") String publicKeyLocation,
                    @Value("${igrp.oauth.keys.private:classpath:keys/private.pem}") String privateKeyLocation) {
        this.resourceLoader = resourceLoader;
        this.publicKeyLocation = publicKeyLocation;
        this.privateKeyLocation = privateKeyLocation;
    }

    /** Loads the configured primary public key. */
    public RSAPublicKey loadPublicKey() throws Exception {
        return loadPublicKey(publicKeyLocation);
    }

    /**
     * OWASP A02 — loads an RSA public key from an arbitrary resource path.
     * Used by {@link JwtTokenConfig} to load secondary keys for key rotation.
     *
     * @param location Spring resource location (classpath:, file:, or absolute path)
     */
    public RSAPublicKey loadPublicKey(String location) throws Exception {
        String key = readResource(location)
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(key);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    /** Loads the configured primary private key. */
    public RSAPrivateKey loadPrivateKey() throws Exception {
        return loadPrivateKey(privateKeyLocation);
    }

    /**
     * OWASP A02 — loads an RSA private key from an arbitrary resource path.
     * Used by {@link JwtTokenConfig} to load secondary keys for key rotation.
     *
     * @param location Spring resource location (classpath:, file:, or absolute path)
     */
    public RSAPrivateKey loadPrivateKey(String location) throws Exception {
        String key = readResource(location)
                .replaceAll("-----BEGIN (.*)-----", "")
                .replaceAll("-----END (.*)-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(key);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    private String readResource(String location) throws Exception {
        Resource resource = resourceLoader.getResource(location);
        try (InputStream in = resource.getInputStream()) {
            return new String(FileCopyUtils.copyToByteArray(in), StandardCharsets.UTF_8);
        }
    }
}
