package cv.igrp.platform.access_management.department.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import cv.igrp.platform.access_management.shared.domain.events.DepartmentScopeChangedEvent;
import cv.igrp.platform.access_management.shared.domain.events.EventPublisher;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ResourceEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.service.ScopeService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class RemoveResourcesFromDepartmentCommandHandler implements CommandHandler<RemoveResourcesFromDepartmentCommand, ResponseEntity<String>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RemoveResourcesFromDepartmentCommandHandler.class);

    private final DepartmentEntityRepository departmentRepository;
    private final ResourceEntityRepository resourceRepository;
    private final EventPublisher eventPublisher;
    private final ScopeService scopeService;


    public RemoveResourcesFromDepartmentCommandHandler(DepartmentEntityRepository departmentRepository,
                                                       ResourceEntityRepository resourceRepository,
                                                       EventPublisher eventPublisher,
                                                       ScopeService scopeService) {
        this.departmentRepository = departmentRepository;
        this.resourceRepository = resourceRepository;
        this.eventPublisher = eventPublisher;
        this.scopeService = scopeService;
    }

    @IgrpCommandHandler
    @Transactional
    public ResponseEntity<String> handle(RemoveResourcesFromDepartmentCommand command) {

        var department = departmentRepository.findByCodeAndStatusNotDeleted(command.getDepartmentCode());

        // Fix (P1 gap): the previous implementation removed the resource from the
        // target department only, with no scope enforcement, no descendant cascade,
        // and no session-invalidation event. Now we enforce scope, cascade to every
        // non-deleted descendant department (child depts can only offer a resource
        // available in an ancestor, so unlinking here must strip it from the whole
        // subtree), and publish DepartmentScopeChangedEvent(CHANGE_RESOURCES) per
        // touched department so users' sessions refresh their route-permission map.
        //
        // ResourceEntity has no direct role association (it maps permissions →
        // routes), so no role-side scrub is needed — the runtime resource check is
        // gated by the dept↔resource link we just cleared.
        scopeService.assertInScope(department.getId());

        Set<DepartmentEntity> subtree = new LinkedHashSet<>();
        collectActiveSubtree(department, subtree);

        for (var resourceName : command.getRemoveResourcesFromDepartmentRequest()) {

            var resource = resourceRepository.findByNameNotDeleted(resourceName);

            Set<String> touchedDeptCodes = new HashSet<>();
            for (DepartmentEntity d : subtree) {
                if (resource.getDepartments().remove(d)) {
                    touchedDeptCodes.add(d.getCode());
                    LOGGER.info("Removed resource '{}' from department '{}'", resourceName, d.getCode());
                }
            }

            resourceRepository.save(resource);

            for (String deptCode : touchedDeptCodes) {
                eventPublisher.publishDepartmentScopeChanged(new DepartmentScopeChangedEvent(
                        deptCode, DepartmentScopeChangedEvent.CHANGE_RESOURCES, null));
            }
        }

        return ResponseEntity.noContent().build();

    }

    /**
     * Depth-first walk of {@code dept} and its non-deleted descendants via
     * {@link DepartmentEntity#getChildrenids()}.
     */
    private void collectActiveSubtree(DepartmentEntity dept, Set<DepartmentEntity> acc) {
        if (dept == null || dept.getStatus() == DepartmentStatus.DELETED) return;
        if (!acc.add(dept)) return;
        if (dept.getChildrenids() != null) {
            for (var child : dept.getChildrenids()) {
                collectActiveSubtree(child, acc);
            }
        }
    }

}
