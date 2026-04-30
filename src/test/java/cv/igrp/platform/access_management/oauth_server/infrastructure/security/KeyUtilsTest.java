package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class KeyUtilsTest {

    @Test
    void loadsRsaKeyPairFromClasspathPem() throws Exception {
        KeyUtils keyUtils = new KeyUtils(new DefaultResourceLoader(),
                "classpath:keys/public.pem", "classpath:keys/private.pem");

        RSAPublicKey pub = keyUtils.loadPublicKey();
        RSAPrivateKey priv = keyUtils.loadPrivateKey();

        assertNotNull(pub);
        assertNotNull(priv);
        assertEquals("RSA", pub.getAlgorithm());
        assertEquals("RSA", priv.getAlgorithm());
        assertEquals(pub.getModulus(), priv.getModulus(),
                "Loaded public and private keys must belong to the same RSA pair");
    }
}
