package cv.igrp.platform.access_management.shared.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.Set;

@Setter
@Getter
@Component
@RequestScope
public class RequestScopeCache {
    private Set<Integer> visibleDepartments;
    private Set<Integer> visibleApplications;
}
