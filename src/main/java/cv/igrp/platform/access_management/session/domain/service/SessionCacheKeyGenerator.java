package cv.igrp.platform.access_management.session.domain.service;

import org.jetbrains.annotations.NotNull;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Component("sessionCacheKeyGenerator")
public class SessionCacheKeyGenerator implements KeyGenerator {
    
    @NotNull
    @Override
    public Object generate(@NotNull Object target, @NotNull Method method, Object... params) {
        if (params.length == 1 && params[0] instanceof String userExternalId) {
            return userExternalId;
        }
        throw new IllegalArgumentException("Session cache key generation requires a single String parameter (userExternalId)");
    }
}
