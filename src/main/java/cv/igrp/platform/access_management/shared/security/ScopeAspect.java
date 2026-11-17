package cv.igrp.platform.access_management.shared.security;

import cv.igrp.platform.access_management.shared.infrastructure.service.ScopeService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class ScopeAspect {

    private final ScopeService scopeService;

    public ScopeAspect(ScopeService scopeService) {
        this.scopeService = scopeService;
    }

    @Around("@annotation(cv.igrp.platform.access_management.shared.infrastructure.spring.Scoped)")
    public Object applyScope(ProceedingJoinPoint pjp) throws Throwable {
        // Row visibility is the starter's scope filter (#25); the menu listing only needs the caller.
        Object[] args = pjp.getArgs();
        for (Object arg : args) {
            if (arg instanceof ScopeContext ctx) {
                ctx.setUserId(scopeService.getActor().id());
                ctx.setSuperAdmin(scopeService.isSuperAdmin());
            }
        }

        return pjp.proceed(args);
    }

}
