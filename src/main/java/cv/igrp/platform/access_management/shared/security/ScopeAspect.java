package cv.igrp.platform.access_management.shared.security;

import cv.igrp.platform.access_management.shared.infrastructure.service.ScopeService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Set;

@Aspect
@Component
public class ScopeAspect {

    private final ScopeService scopeService;

    public ScopeAspect(ScopeService scopeService) {
        this.scopeService = scopeService;
    }

    @Around("@annotation(cv.igrp.platform.access_management.shared.infrastructure.spring.Scoped)")
    public Object applyScope(ProceedingJoinPoint pjp) throws Throwable {
        // Your repository or service logic will receive scoped IDs transparently
        Set<Integer> allowedDepartments = scopeService.getVisibleDepartmentIds();
        Set<Integer> allowedApplications = scopeService.getVisibleApplicationIds();
        Set<Integer> allowedRoles = scopeService.getVisibleRoleIds();

        // Now you can inject these into your service parameters automatically
        Object[] args = pjp.getArgs();
        for (Object arg : args) {
            if (arg instanceof ScopeContext ctx) {
                ctx.setDepartmentIds(allowedDepartments);
                ctx.setApplicationIds(allowedApplications);
                ctx.setRoleIds(allowedRoles);
                ctx.setExternalUserId(scopeService.getActor().externalId());
                ctx.setSuperAdmin(scopeService.isSuperAdmin());
            }
        }

        return pjp.proceed(args);
    }

}
