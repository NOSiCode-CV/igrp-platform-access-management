package cv.igrp.platform.access_management.shared.infrastructure.cache;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CacheEvictionInterceptor implements HandlerInterceptor {

    private final Logger LOGGER = LoggerFactory.getLogger(CacheEvictionInterceptor.class);
    private static final Pattern USER_PATH_PATTERN = Pattern.compile("^/api/users/([^/]+)(?:/.*)?$");
    private static final Pattern DEPARTMENT_PATH_PATTERN = Pattern.compile("^/api/departments/([^/]+)(?:/.*)?$");
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
            evictTargeted(request);
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

    private void evictTargeted(HttpServletRequest request) {
        String uri = request.getRequestURI();

        Matcher userMatcher = USER_PATH_PATTERN.matcher(uri);
        if (userMatcher.matches()) {
            String userId = userMatcher.group(1);
            evictService.evictByUserId(userId);
            return;
        }

        Matcher departmentMatcher = DEPARTMENT_PATH_PATTERN.matcher(uri);
        if (departmentMatcher.matches()) {
            String departmentCode = departmentMatcher.group(1);
            evictService.evictByDepartment(departmentCode);
            return;
        }

        if (uri.matches("^/api/m2m/sync(/.*)?$")) {
            String roleCode = request.getParameter("roleCode");
            if (roleCode != null && !roleCode.isBlank()) {
                evictService.evictByRole(roleCode);
                return;
            }

            String departmentCode = request.getParameter("departmentCode");
            if (departmentCode != null && !departmentCode.isBlank()) {
                evictService.evictByDepartment(departmentCode);
                return;
            }

            String subject = request.getParameter("subject");
            if (subject != null && !subject.isBlank()) {
                evictService.evictBySubject(subject);
                return;
            }

            LOGGER.info("Skipping cache eviction for {}: no targeted parameters found", uri);
        }
    }
}
