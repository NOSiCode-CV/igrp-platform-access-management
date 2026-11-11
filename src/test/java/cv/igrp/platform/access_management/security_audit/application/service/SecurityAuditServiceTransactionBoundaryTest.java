package cv.igrp.platform.access_management.security_audit.application.service;

import cv.igrp.platform.access_management.security_audit.domain.enums.AuditCategory;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditEventType;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditStatus;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsArea;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsEntityType;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsOperation;
import cv.igrp.platform.access_management.security_audit.infrastructure.persistence.SecurityAuditLogRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression guard for the self-invocation defect that silently dropped every
 * audit event written through a convenience method.
 *
 * <p>{@code SecurityAuditChainService.append} is {@code @Transactional(MANDATORY)}.
 * The convenience methods reach it by calling {@code logEvent} on {@code this},
 * which bypasses the Spring AOP proxy — so an annotation on {@code logEvent}
 * alone never starts a transaction and {@code append} throws
 * {@code IllegalTransactionStateException}, swallowed by the fail-safe catch.
 *
 * <p>Access events begin a {@code REQUIRES_NEW} transaction; settings events join
 * the caller's with {@code REQUIRED} (v3-23).
 *
 * <p>Unlike the plain-Mockito tests, this one wires the real bean through a
 * transactional proxy so the boundary is exercised: it asserts that <em>every</em>
 * public entry point causes the transaction manager to begin a transaction with
 * the propagation of its write path on the way in. A new public method that forgets
 * the annotation fails here.
 */
@SpringJUnitConfig(SecurityAuditServiceTransactionBoundaryTest.TestConfig.class)
class SecurityAuditServiceTransactionBoundaryTest {

    /**
     * Only the service under test and the transaction manager are beans. The
     * collaborators are held as plain mocks rather than registered, so Spring's
     * persistence post-processor never tries to satisfy the
     * {@code @PersistenceContext} field that {@link SecurityAuditChainService}
     * declares — this context has no JPA at all.
     */
    @Configuration
    @EnableTransactionManagement
    static class TestConfig {

        static final SecurityAuditLogRepository REPOSITORY = mock(SecurityAuditLogRepository.class);
        static final SecurityAuditContextProvider CONTEXT_PROVIDER = mock(SecurityAuditContextProvider.class);
        static final SecurityAuditChainService CHAIN_SERVICE = mock(SecurityAuditChainService.class);

        @Bean
        PlatformTransactionManager transactionManager() {
            return mock(PlatformTransactionManager.class);
        }

        @Bean
        SecurityAuditService securityAuditService() {
            return new SecurityAuditServiceImpl(REPOSITORY, CONTEXT_PROVIDER, CHAIN_SERVICE, new SimpleMeterRegistry());
        }
    }

    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private SecurityAuditService service;

    @BeforeEach
    void setUp() {
        // The Spring context is cached across the parameterized runs.
        reset(transactionManager, TestConfig.CONTEXT_PROVIDER, TestConfig.CHAIN_SERVICE);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus(true));
        when(TestConfig.CONTEXT_PROVIDER.getContext()).thenReturn(Map.of());
    }

    static Stream<Arguments> publicEntryPoints() {
        return Stream.of(
                Arguments.of("logEvent", TransactionDefinition.PROPAGATION_REQUIRES_NEW, (Consumer<SecurityAuditService>) s ->
                        s.logEvent(AuditEventType.LOGIN_SUCCESS, AuditCategory.AUTHENTICATION, Map.of())),
                Arguments.of("logEvent(with report context)", TransactionDefinition.PROPAGATION_REQUIRES_NEW, (Consumer<SecurityAuditService>) s ->
                        s.logEvent(AuditEventType.LOGIN_SUCCESS, AuditCategory.AUTHENTICATION, Map.of(),
                                AuditReportContext.builder().module("Transfers").build())),
                Arguments.of("logAuthenticationSuccess", TransactionDefinition.PROPAGATION_REQUIRES_NEW, (Consumer<SecurityAuditService>)
                        SecurityAuditService::logAuthenticationSuccess),
                Arguments.of("logAuthenticationFailure", TransactionDefinition.PROPAGATION_REQUIRES_NEW, (Consumer<SecurityAuditService>) s ->
                        s.logAuthenticationFailure("bad credentials")),
                Arguments.of("logProfileSwitch", TransactionDefinition.PROPAGATION_REQUIRES_NEW, (Consumer<SecurityAuditService>) s ->
                        s.logProfileSwitch(1, 2)),
                Arguments.of("logAccessDenied(permission)", TransactionDefinition.PROPAGATION_REQUIRES_NEW, (Consumer<SecurityAuditService>) s ->
                        s.logAccessDenied("igrp.audit.view")),
                Arguments.of("logAccessDenied(permission, reason)", TransactionDefinition.PROPAGATION_REQUIRES_NEW, (Consumer<SecurityAuditService>) s ->
                        s.logAccessDenied("igrp.audit.view", "no grant")),
                Arguments.of("logUserChange", TransactionDefinition.PROPAGATION_REQUIRES_NEW, (Consumer<SecurityAuditService>) s ->
                        s.logUserChange("user-1", "CREATE")),
                Arguments.of("logSettingsEvent(7-arg)", TransactionDefinition.PROPAGATION_REQUIRED, (Consumer<SecurityAuditService>) s ->
                        s.logSettingsEvent(SettingsArea.ACCESS, SettingsEntityType.ROLE, SettingsOperation.CREATE,
                                "TEST.Administrator", null, null, null)),
                Arguments.of("logSettingsEvent(8-arg)", TransactionDefinition.PROPAGATION_REQUIRED, (Consumer<SecurityAuditService>) s ->
                        s.logSettingsEvent(SettingsArea.ACCESS, SettingsEntityType.ROLE, SettingsOperation.CREATE,
                                "TEST.Administrator", null, null, null, AuditStatus.SUCCESS))
        );
    }

    @ParameterizedTest(name = "{0} begins the transaction its write path needs")
    @MethodSource("publicEntryPoints")
    void everyPublicEntryPointBeginsItsOwnTransaction(String name, int propagation, Consumer<SecurityAuditService> call) {
        call.accept(service);

        ArgumentCaptor<TransactionDefinition> captor = ArgumentCaptor.forClass(TransactionDefinition.class);
        // Exactly once: the proxy begins the transaction on entry and the inner
        // self-invocation joins it rather than starting a second one.
        verify(transactionManager).getTransaction(captor.capture());
        assertThat(captor.getValue().getPropagationBehavior())
                // Access events: REQUIRES_NEW, so they survive the caller's rollback.
                // Settings events: REQUIRED, so they commit or roll back with the change (v3-23).
                .as("%s must be proxied with the propagation of its write path", name)
                .isEqualTo(propagation);
    }

    @ParameterizedTest(name = "{0} reaches the hash chain")
    @MethodSource("publicEntryPoints")
    void everyPublicEntryPointReachesTheChain(String name, int propagation, Consumer<SecurityAuditService> call) {
        call.accept(service);

        // Proves the event is actually appended rather than lost to the fail-safe catch.
        verify(TestConfig.CHAIN_SERVICE).append(any());
    }
}
