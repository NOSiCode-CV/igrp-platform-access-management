package cv.igrp.platform.access_management.security_audit.application.queries;

import cv.igrp.framework.core.domain.Query;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetSecurityAuditLogByIdQuery implements Query {

    private Long id;

}
