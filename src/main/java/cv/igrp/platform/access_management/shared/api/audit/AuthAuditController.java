package cv.igrp.platform.access_management.shared.api.audit;

import cv.igrp.framework.core.domain.QueryBus;
import cv.igrp.framework.stereotype.IgrpController;
import cv.igrp.platform.access_management.security_audit.application.dto.SecurityAuditLogDTO;
import cv.igrp.platform.access_management.security_audit.application.queries.GetSecurityAuditLogByIdQuery;
import cv.igrp.platform.access_management.security_audit.application.queries.GetSecurityAuditLogsByUserIdQuery;
import cv.igrp.platform.access_management.security_audit.application.queries.GetSecurityAuditLogsQuery;
import cv.igrp.platform.access_management.shared.infrastructure.authorization.permission.AuditPermissions;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController("sharedAuthAuditController")
@IgrpController
@RequestMapping("/api/auth/audit")
public class AuthAuditController {

    private final QueryBus queryBus;

    public AuthAuditController(QueryBus queryBus) {
        this.queryBus = queryBus;
    }

    @GetMapping
    public ResponseEntity<Page<SecurityAuditLogDTO>> list(Pageable pageable) {
        return queryBus.handle(new GetSecurityAuditLogsQuery(null, null, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SecurityAuditLogDTO> getById(@PathVariable Long id) {
        return queryBus.handle(new GetSecurityAuditLogByIdQuery(id));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<Page<SecurityAuditLogDTO>> getByUserId(@PathVariable String userId, Pageable pageable) {
        return queryBus.handle(new GetSecurityAuditLogsByUserIdQuery(userId, pageable));
    }
}
