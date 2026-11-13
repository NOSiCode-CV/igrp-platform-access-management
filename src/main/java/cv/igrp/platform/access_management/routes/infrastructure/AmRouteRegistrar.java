package cv.igrp.platform.access_management.routes.infrastructure;

import cv.igrp.framework.auth.core.route.RouteCatalog;
import cv.igrp.framework.auth.core.route.RouteRegistrar;
import cv.igrp.platform.access_management.routes.application.dto.RouteRegistrationRequestDTO;
import cv.igrp.platform.access_management.routes.application.dto.RouteRegistrationRequestDTO.RegisteredRouteDTO;
import cv.igrp.platform.access_management.routes.application.dto.RouteRegistrationResultDTO;
import cv.igrp.platform.access_management.routes.domain.service.PermissionHolders;
import cv.igrp.platform.access_management.routes.domain.service.RouteRegistrationService;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.service.ConfigurationService;
import cv.igrp.platform.access_management.shared.security.SelfServicePaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.time.Duration;
import java.util.List;

/**
 * Registers AM's own routes (#36), as the starter registers a service's, but
 * in-process: through {@link RouteRegistrationService#registerOwn} instead of
 * {@code POST /api/routes/register} with a service account. Replaces the
 * starter's registrar, which is {@code @ConditionalOnMissingBean}.
 *
 * <p>Runs once the application is ready, after {@code ConfigurationService}
 * has created AM's application ({@value ConfigurationService#IGRP_APP}), which
 * owns the keys. The self-service routes ({@link SelfServicePaths}) and the
 * public ones ({@code igrp.security.public-paths}, such as the OpenAPI document)
 * are not registered: they need no key.
 */
@Component
public class AmRouteRegistrar extends RouteRegistrar {

    private static final Logger LOGGER = LoggerFactory.getLogger(AmRouteRegistrar.class);

    private final ApplicationContext context;
    private final RouteRegistrationService registrations;
    private final ApplicationEntityRepository applications;
    private final String service;
    private final String version;
    private final List<PathPattern> publicPaths;
    private final PermissionHolders holders;

    public AmRouteRegistrar(ApplicationContext context, RouteRegistrationService registrations,
                            ApplicationEntityRepository applications, PermissionHolders holders,
                            @Value("${igrp.security.service-name}") String service,
                            @Value("${igrp.security.service-version}") String version,
                            @Value("${igrp.security.public-paths:}") List<String> publicPaths) {
        super(context, service, version, new AccessManagement("", "", "", "", Duration.ZERO));
        this.context = context;
        this.registrations = registrations;
        this.applications = applications;
        this.holders = holders;
        this.service = service;
        this.version = version;
        this.publicPaths = publicPaths.stream().filter(path -> !path.isBlank())
                .map(path -> PathPatternParser.defaultInstance.parse(path.trim())).toList();
    }

    /** The starter's registrar calls AM over HTTP here; AM registers on {@link #registerOwnRoutes()}. */
    @Override
    public void afterSingletonsInstantiated() {
        // Registered once ready, in-process.
    }

    private boolean isPublic(String path) {
        PathContainer container = PathContainer.parsePath(path);
        return publicPaths.stream().anyMatch(pattern -> pattern.matches(container));
    }

    /**
     * First of the ready listeners: the default service account's bootstrap
     * grants these keys. A failure is logged, not fatal, as in the starter: the
     * keys of the previous startup stay, and every route stays closed without
     * its key, so AM keeps serving root and every grant already made.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(Integer.MIN_VALUE)
    public void registerOwnRoutes() {
        RequestMappingHandlerMapping mapping =
                context.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
        List<RegisteredRouteDTO> routes = RouteCatalog.collect(service, mapping.getHandlerMethods()).stream()
                .filter(route -> !SelfServicePaths.matches(route.method(), route.path()) && !isPublic(route.path()))
                .map(route -> new RegisteredRouteDTO(route.method(), route.path(), route.key(), route.annotated(),
                        route.name(), route.description(), null))
                .toList();
        try {
            ApplicationEntity application = applications.findByCodeAndStatusNot(ConfigurationService.IGRP_APP,
                    Status.DELETED).orElseThrow(() -> new IllegalStateException(
                    "AM's application " + ConfigurationService.IGRP_APP + " does not exist"));
            RouteRegistrationResultDTO result = registrations.registerOwn(
                    new RouteRegistrationRequestDTO(service, version, routes), application);
            LOGGER.info("[ROUTES] AM's own {} routes ({}) registered: {}", routes.size(), version, result);
            holders.evictHoldersOf(service);
        } catch (RuntimeException ex) {
            LOGGER.error("[ROUTES] Could not register AM's own {} routes; the keys of the previous startup stay "
                    + "and the next startup registers them again.", routes.size(), ex);
        }
    }
}
