package cv.igrp.platform.access_management.authorization.domain.service;

import cv.igrp.framework.auth.core.authorization.model.PermissionCheckRequest;
import org.jetbrains.annotations.NotNull;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Component("permissionCacheKeyGenerator")
public class PermissionCacheKeyGenerator implements KeyGenerator {
    @NotNull
    @Override
    public Object generate(@NotNull Object target, @NotNull Method method, Object... params) {
        if (params.length == 1 && params[0] instanceof PermissionCheckRequest req) {
            return String.join(":", req.getSubject(), req.getResource(), req.getAction());
        }
        throw new IllegalArgumentException("Unsupported key generation");
    }
}
