package cv.igrp.platform.access_management.central_audit.interfaces.rest;

import cv.igrp.framework.stereotype.IgrpController;
import cv.igrp.platform.access_management.central_audit.application.dto.BusinessAuditChainReportDTO;
import cv.igrp.platform.access_management.central_audit.application.dto.BusinessAuditRowDTO;
import cv.igrp.platform.access_management.central_audit.domain.service.BusinessAuditQueries;
import cv.igrp.platform.access_management.central_audit.domain.service.BusinessAuditValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * v3-22 (#32): the central store of business audit events. Keys
 * {@code access-management-api.auth.audit.business.list} and
 * {@code access-management-api.auth.audit.business.validate.list}.
 */
@IgrpController
@RestController
@RequestMapping(path = "api/auth/audit/business")
@Tag(name = "Business audit", description = "Every service's business audit events, in one store")
public class BusinessAuditController {

    private final BusinessAuditQueries queries;
    private final BusinessAuditValidator validator;

    public BusinessAuditController(BusinessAuditQueries queries, BusinessAuditValidator validator) {
        this.queries = queries;
        this.validator = validator;
    }

    @GetMapping
    @Operation(summary = "Search business audit events",
            description = "Newest first; every filter is optional. size at most 200.")
    public ResponseEntity<List<BusinessAuditRowDTO>> search(
            @RequestParam(value = "service", required = false) String service,
            @RequestParam(value = "username", required = false) String username,
            @RequestParam(value = "event", required = false) String event,
            @RequestParam(value = "resource", required = false) String resource,
            @RequestParam(value = "traceId", required = false) String traceId,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size) {
        return ResponseEntity.ok(queries.search(new BusinessAuditQueries.Filter(service, username, event, resource,
                traceId, from, to), page, size));
    }

    @GetMapping("validate")
    @Operation(summary = "Check a service's hash chain",
            description = "The first row whose hash, previous hash or place does not hold, if any.")
    public ResponseEntity<BusinessAuditChainReportDTO> validate(@RequestParam("service") String service) {
        return ResponseEntity.ok(validator.validate(service));
    }
}
