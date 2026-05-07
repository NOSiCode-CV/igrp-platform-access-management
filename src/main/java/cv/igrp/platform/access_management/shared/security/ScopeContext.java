package cv.igrp.platform.access_management.shared.security;

import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class ScopeContext {
    private boolean isSuperAdmin;
    private Integer userId;
    private Set<Integer> departmentIds;
    private Set<Integer> applicationIds;
    private Set<Integer> roleIds;
}