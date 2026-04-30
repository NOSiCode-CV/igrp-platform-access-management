package cv.igrp.platform.access_management.app.specs;

import cv.igrp.platform.access_management.app.application.queries.GetApplicationMenusQuery;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.MenuEntryEntity;
import cv.igrp.platform.access_management.shared.infrastructure.spring.Scoped;
import cv.igrp.platform.access_management.shared.security.ScopeContext;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class MenuSpecificationBuilder {

    Logger logger = LoggerFactory.getLogger(MenuSpecificationBuilder.class);

    /**
     * Builds a dynamic JPA {@link Specification} based on app code and status.
     *
     * @param query   the get menus query
     * @param context the user scope context
     * @return a {@link Specification} representing the composed query filters
     */
    @Scoped
    public Specification<MenuEntryEntity> buildSpecification(GetApplicationMenusQuery query, ScopeContext context) {
        Specification<MenuEntryEntity> spec = Specification.allOf();

        if (query.getCode() != null && !query.getCode().isEmpty()) {
            spec = spec.and((root, q, cb) -> {
                Join<Object, Object> applicationJoin = root.join("applicationId", JoinType.INNER);
                return cb.equal(cb.lower(applicationJoin.get("code")), query.getCode().toLowerCase());
            });
        }

        // Exclude deleted menus
        spec = spec.and((root, criteriaQuery, cb) ->
                cb.notEqual(root.get("status"), Status.DELETED)
        );

        // Apply scope
        spec = applyScope(spec, context);

        return spec;
    }

    private Specification<MenuEntryEntity> applyScope(Specification<MenuEntryEntity> spec, ScopeContext context) {

        if (!context.isSuperAdmin()) {
            return spec.and((root, query, cb) -> {

                if(query != null) query.distinct(true);

                var roleJoin = root.join("roles");
                var userJoin = roleJoin.join("users");

                return cb.equal(userJoin.get("externalId"), context.getExternalUserId());
            });
        }

        return spec;

    }


}
