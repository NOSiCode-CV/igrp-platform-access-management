package cv.igrp.platform.access_management.shared.security;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScopeContext {
    private boolean isSuperAdmin;
    private String userId;
}