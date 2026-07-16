package cv.igrp.platform.access_management.security_audit.application.queries;

import cv.igrp.platform.access_management.security_audit.domain.entities.SecurityAuditLogEntity;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditStatus;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsArea;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SecurityAuditSpecificationBuilderTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS) private Root<SecurityAuditLogEntity> root;
    @Mock private CriteriaQuery<?> query;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS) private CriteriaBuilder cb;

    private void apply(Specification<SecurityAuditLogEntity> spec) {
        spec.toPredicate(root, query, cb);
    }

    @Test
    void parseEnumIsCaseInsensitiveAndNullSafe() {
        assertThat(SecurityAuditSpecificationBuilder.parseEnum(AuditStatus.class, "success"))
                .contains(AuditStatus.SUCCESS);
        assertThat(SecurityAuditSpecificationBuilder.parseEnum(AuditStatus.class, "  ACCESS_DENIED "))
                .contains(AuditStatus.ACCESS_DENIED);
        assertThat(SecurityAuditSpecificationBuilder.parseEnum(AuditStatus.class, "nope"))
                .isEmpty();
    }

    @Test
    void likeIgnoreCaseBuildsALikePredicate() {
        apply(SecurityAuditSpecificationBuilder.create()
                .likeIgnoreCase("username", "Alice")
                .build());
        verify(cb).like(any(), eq("%alice%"));
    }

    @Test
    void blankFilterAddsNoPredicate() {
        apply(SecurityAuditSpecificationBuilder.create()
                .likeIgnoreCase("username", "  ")
                .build());
        verify(cb, never()).like(any(), any(String.class));
    }

    @Test
    void enumEqualsWithValidValueBuildsEquality() {
        apply(SecurityAuditSpecificationBuilder.create()
                .enumEquals("settingsArea", SettingsArea.class, "access")
                .build());
        verify(cb).equal(any(), eq(SettingsArea.ACCESS));
    }

    @Test
    void enumEqualsWithInvalidValueBuildsEmptyResult() {
        apply(SecurityAuditSpecificationBuilder.create()
                .enumEquals("settingsArea", SettingsArea.class, "not-an-area")
                .build());
        verify(cb).disjunction();
    }

    @Test
    void timestampBetweenBuildsBothBounds() {
        LocalDateTime start = LocalDateTime.of(2026, 7, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 7, 15, 0, 0);
        apply(SecurityAuditSpecificationBuilder.create()
                .timestampBetween(start, end)
                .build());
        verify(cb).greaterThanOrEqualTo(any(), eq(start));
        verify(cb).lessThanOrEqualTo(any(), eq(end));
    }

    @Test
    void createExcludesGenesisAnchor() {
        Specification<SecurityAuditLogEntity> spec = SecurityAuditSpecificationBuilder.create().build();
        assertThat(spec).isNotNull();
        apply(spec);
        verify(cb).notEqual(any(), eq(0L));
    }
}
