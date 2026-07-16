package cv.igrp.platform.access_management.security_audit.application.service;

import cv.igrp.platform.access_management.security_audit.domain.enums.AuditStatus;

import java.time.Instant;

/**
 * Report-facing detail a caller can attach to an audit event, populating the
 * typed columns behind the Audit and Access reports (requirements.md §1.5.1 /
 * §1.5.2, plan.md §Phase 2 step 4).
 *
 * <p>Every field is optional. Where a caller leaves one out,
 * {@code SecurityAuditServiceImpl} derives what it honestly can from the event
 * itself and the ambient request context ({@code status}, {@code action},
 * {@code accessRole}, {@code module}). The rest — {@code operationState},
 * {@code authorizedBy} and the {@code period} bounds — have no meaningful
 * derivation and stay null unless supplied here.
 */
public record AuditReportContext(
        String module,
        String accessRole,
        String operationState,
        String authorizedBy,
        AuditStatus status,
        String action,
        Instant periodStart,
        Instant periodEnd) {

    private static final AuditReportContext EMPTY = builder().build();

    /** No caller-supplied detail — every column is derived or left null. */
    public static AuditReportContext empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String module;
        private String accessRole;
        private String operationState;
        private String authorizedBy;
        private AuditStatus status;
        private String action;
        private Instant periodStart;
        private Instant periodEnd;

        public Builder module(String module) {
            this.module = module;
            return this;
        }

        public Builder accessRole(String accessRole) {
            this.accessRole = accessRole;
            return this;
        }

        public Builder operationState(String operationState) {
            this.operationState = operationState;
            return this;
        }

        public Builder authorizedBy(String authorizedBy) {
            this.authorizedBy = authorizedBy;
            return this;
        }

        public Builder status(AuditStatus status) {
            this.status = status;
            return this;
        }

        public Builder action(String action) {
            this.action = action;
            return this;
        }

        public Builder period(Instant start, Instant end) {
            this.periodStart = start;
            this.periodEnd = end;
            return this;
        }

        public AuditReportContext build() {
            return new AuditReportContext(
                    module, accessRole, operationState, authorizedBy, status, action, periodStart, periodEnd);
        }
    }
}
