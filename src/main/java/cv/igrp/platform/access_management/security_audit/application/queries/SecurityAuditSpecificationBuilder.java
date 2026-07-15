package cv.igrp.platform.access_management.security_audit.application.queries;

import cv.igrp.platform.access_management.security_audit.domain.entities.SecurityAuditLogEntity;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Fluent builder that translates report filters into a JPA
 * {@link Specification} over {@link SecurityAuditLogEntity}. Shared by the three
 * report query handlers (Audit / Access / Settings) so filter semantics stay
 * identical across them (plan.md §Phase 2.6).
 *
 * <p>Every {@code with*} method is a no-op when its value is {@code null}/blank,
 * so handlers can pass optional filters unconditionally. The GENESIS anchor
 * (sequence 0) is always excluded.
 */
public final class SecurityAuditSpecificationBuilder {

    private final List<Specification<SecurityAuditLogEntity>> specs = new ArrayList<>();

    private SecurityAuditSpecificationBuilder() {
    }

    /** New builder that already excludes the GENESIS anchor row. */
    public static SecurityAuditSpecificationBuilder create() {
        return new SecurityAuditSpecificationBuilder().excludeGenesis();
    }

    public SecurityAuditSpecificationBuilder excludeGenesis() {
        specs.add((root, q, cb) -> cb.or(
                cb.isNull(root.get("sequenceNumber")),
                cb.notEqual(root.get("sequenceNumber"), 0L)));
        return this;
    }

    /** Inclusive {@code timestamp} range. Both bounds are optional. */
    public SecurityAuditSpecificationBuilder timestampBetween(LocalDateTime start, LocalDateTime end) {
        if (start != null) {
            specs.add((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("timestamp"), start));
        }
        if (end != null) {
            specs.add((root, q, cb) -> cb.lessThanOrEqualTo(root.get("timestamp"), end));
        }
        return this;
    }

    /** Exact match on a field. */
    public SecurityAuditSpecificationBuilder equalsField(String field, Object value) {
        if (value != null && !(value instanceof String s && s.isBlank())) {
            specs.add((root, q, cb) -> cb.equal(root.get(field), value));
        }
        return this;
    }

    /** Case-insensitive substring match on a string field. */
    public SecurityAuditSpecificationBuilder likeIgnoreCase(String field, String value) {
        if (value != null && !value.isBlank()) {
            String pattern = "%" + value.toLowerCase() + "%";
            specs.add((root, q, cb) -> cb.like(cb.lower(root.get(field)), pattern));
        }
        return this;
    }

    /** Restrict to rows where {@code field} is non-null (e.g. settings-only rows). */
    public SecurityAuditSpecificationBuilder fieldIsNotNull(String field) {
        specs.add((root, q, cb) -> cb.isNotNull(root.get(field)));
        return this;
    }

    /**
     * Match an enum-valued field from a raw string filter; a value that does not
     * parse to the enum adds an always-false predicate (empty result), matching
     * the raw-audit list handler's behaviour.
     */
    public <E extends Enum<E>> SecurityAuditSpecificationBuilder enumEquals(String field, Class<E> type, String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return this;
        }
        Optional<E> parsed = parseEnum(type, rawValue);
        if (parsed.isPresent()) {
            specs.add((root, q, cb) -> cb.equal(root.get(field), parsed.get()));
        } else {
            specs.add((root, q, cb) -> cb.disjunction()); // no match
        }
        return this;
    }

    public Specification<SecurityAuditLogEntity> build() {
        Specification<SecurityAuditLogEntity> combined = null;
        for (Specification<SecurityAuditLogEntity> spec : specs) {
            combined = combined == null ? spec : combined.and(spec);
        }
        return combined;
    }

    public static <E extends Enum<E>> Optional<E> parseEnum(Class<E> type, String raw) {
        try {
            return Optional.of(Enum.valueOf(type, raw.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
