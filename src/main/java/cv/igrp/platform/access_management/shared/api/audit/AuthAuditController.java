package cv.igrp.platform.access_management.shared.api.audit;

import cv.igrp.framework.core.domain.QueryBus;
import cv.igrp.framework.stereotype.IgrpController;
import cv.igrp.platform.access_management.security_audit.application.dto.SecurityAuditLogDTO;
import cv.igrp.platform.access_management.security_audit.application.queries.GetSecurityAuditLogsQuery;
import cv.igrp.platform.access_management.security_audit.application.service.SecurityAuditChainService;
import cv.igrp.platform.access_management.security_audit.application.service.SecurityAuditChainValidator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Unified audit-trail endpoint. Backs the {@code AuditLog} SDK client.
 *
 * <p>Phase 1 of the Unified Audit &amp; Reports feature collapsed the three legacy
 * GETs ({@code /}, {@code /{id}}, {@code /user/{userId}}) into a single filtered,
 * paginated list (R1.3 / N6) and added tamper-evidence controls: on-demand chain
 * validation and a gated purge.
 */
@RestController("sharedAuthAuditController")
@IgrpController
@RequestMapping("/api/auth/audit")
public class AuthAuditController {

    private final QueryBus queryBus;
    private final SecurityAuditChainValidator chainValidator;
    private final SecurityAuditChainService chainService;

    public AuthAuditController(QueryBus queryBus,
                               SecurityAuditChainValidator chainValidator,
                               SecurityAuditChainService chainService) {
        this.queryBus = queryBus;
        this.chainValidator = chainValidator;
        this.chainService = chainService;
    }

    /**
     * Paginated, filtered view of the unified audit log. All filters are optional.
     */
    @GetMapping
    @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_AUDIT_VIEW)")
    public ResponseEntity<Page<SecurityAuditLogDTO>> list(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String ipAddress,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            Pageable pageable) {
        AuditDateRange.require(startDate, endDate);
        return queryBus.handle(new GetSecurityAuditLogsQuery(
                userId, username, eventType, category, ipAddress, startDate, endDate, pageable));
    }

    /**
     * Verify the tamper-evident hash chain on demand. Returns
     * {@code {valid, rows_checked, broken_at}}.
     */
    @PostMapping("/validate")
    @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_AUDIT_VIEW)")
    public ResponseEntity<Map<String, Object>> validate() {
        SecurityAuditChainValidator.Result result = chainValidator.validate();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("valid", result.valid());
        body.put("rows_checked", result.rowsChecked());
        body.put("broken_at", result.brokenAt());
        // Rows that predate the hash chain (V10_1 left them unhashed; prod never
        // rehashes). Skipped by the walk, reported here so the count stays honest.
        body.put("unverifiable_legacy_rows", result.unverifiableLegacyRows());
        return ResponseEntity.ok(body);
    }

    /**
     * Purge every audit row except the GENESIS anchor and restart the chain.
     * Separately gated by {@code igrp.audit.purge} (R4.2).
     */
    @DeleteMapping("/purge")
    @PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_AUDIT_PURGE)")
    public ResponseEntity<Map<String, Object>> purge() {
        long removed = chainService.purge();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("purged", removed);
        return ResponseEntity.ok(body);
    }
}
