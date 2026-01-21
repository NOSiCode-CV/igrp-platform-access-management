package cv.igrp.platform.access_management.shared.infrastructure.cache;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class CacheEvictionInterceptor implements HandlerInterceptor {

    private final Logger LOGGER = LoggerFactory.getLogger(CacheEvictionInterceptor.class);
    private final PermissionCacheEvictService evictService;

    public CacheEvictionInterceptor(PermissionCacheEvictService evictService) {
        this.evictService = evictService;
    }

    @Override
    public void afterCompletion(@NotNull HttpServletRequest request,
                                @NotNull HttpServletResponse response,
                                @NotNull Object handler,
                                Exception ex) {

        if (ex != null) {
            return; // request failed with exception
        }

        if (!isWriteMethod(request.getMethod())) {
            return;
        }

        int status = response.getStatus();

        if (status >= 200 && status < 400 && matchesEvictionPath(request.getRequestURI())) {
            evictService.evictAll();
        }

    }

    private boolean isWriteMethod(String method) {
        return "POST".equals(method)
                || "PUT".equals(method)
                || "PATCH".equals(method)
                || "DELETE".equals(method);
    }

    private boolean matchesEvictionPath(String uri) {
        return uri.matches("^/api/departments/[^/]+(/.*)?$")
                || uri.matches("^/api/m2m/sync(/.*)?$")
                || uri.matches("^/api/users/\\d+(/.*)?$");
    }
}
