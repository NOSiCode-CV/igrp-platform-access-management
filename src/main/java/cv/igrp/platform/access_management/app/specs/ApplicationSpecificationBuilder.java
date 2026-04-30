package cv.igrp.platform.access_management.app.specs;

import cv.igrp.platform.access_management.app.application.queries.GetApplicationsQuery;
import cv.igrp.platform.access_management.shared.application.constants.AppType;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.spring.Scoped;
import cv.igrp.platform.access_management.shared.security.ScopeContext;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class ApplicationSpecificationBuilder {

    Logger logger = LoggerFactory.getLogger(ApplicationSpecificationBuilder.class);

    /**
     * Builds a dynamic JPA {@link Specification} based on optional code and name filters.
     *
     * @param query   the get applications query
     * @param context the user scope context
     * @return a {@link Specification} representing the composed query filters
     */
    @Scoped
    public Specification<ApplicationEntity> buildSpecification(GetApplicationsQuery query, ScopeContext context) {
        Specification<ApplicationEntity> spec = Specification.allOf();
        if (query.getCode() != null && !query.getCode().isEmpty()) {
            spec = spec.and((root, criteriaQuery, cb) ->
                    cb.equal(root.get("code"), query.getCode())
            );
        }
        if (query.getName() != null && !query.getName().isEmpty()) {
            spec = spec.and((root, criteriaQuery, cb) ->
                    cb.like(cb.lower(root.get("name")), "%" + query.getName().toLowerCase() + "%")
            );
        }
        if (query.getSlug() != null && !query.getSlug().isEmpty()) {
            spec = spec.and((root, criteriaQuery, cb) ->
                    cb.equal(cb.lower(root.get("slug")), query.getSlug().toLowerCase())
            );
        }
        if (query.getType() != null && !query.getType().isEmpty()) {
            spec = spec.and((root, criteriaQuery, cb) ->
                    cb.equal(root.get("type"), AppType.fromCodeOrThrow(query.getType()))
            );
        }

        if (query.getDepartmentCode() != null && !query.getDepartmentCode().isEmpty()) {
            spec = spec.and((root, q, cb) -> {
                Join<Object, Object> departmentJoin = root.join("departments", JoinType.INNER);
                return cb.equal(cb.lower(departmentJoin.get("code")), query.getDepartmentCode().toLowerCase());
            });
        }

        // Exclude deleted applications
        spec = spec.and((root, criteriaQuery, cb) ->
                cb.notEqual(root.get("status"), Status.DELETED)
        );

        // Exclude system applications
        //spec = spec.and((root, _, cb) ->
        //        cb.notEqual(root.get("type"), AppType.SYSTEM)
        //);

        // Apply scope
        spec = applyScope(spec, context);

        return spec;
    }

    private Specification<ApplicationEntity> applyScope(Specification<ApplicationEntity> spec, ScopeContext context) {

        if (!context.isSuperAdmin()) {
            return spec.and((root, criteriaQuery, cb) ->
                    root.get("id").in(context.getApplicationIds())
            );
        }

        return spec;

    }


}
