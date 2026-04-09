package cv.igrp.platform.access_management.security_audit.application.queries;

import cv.igrp.framework.core.domain.Query;
import org.springframework.data.domain.Pageable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetSecurityAuditLogsByUserIdQuery implements Query {

    private String userId;
    private Pageable pageable;

}
