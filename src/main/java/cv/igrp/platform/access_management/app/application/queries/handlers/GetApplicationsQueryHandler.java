package cv.igrp.platform.access_management.app.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.app.application.dto.ApplicationDTO;
import cv.igrp.platform.access_management.app.mapper.ApplicationMapper;
import cv.igrp.platform.access_management.shared.domain.models.Application;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ApplicationRepository;
import org.springframework.context.event.EventListener;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.app.application.queries.queries.GetApplicationsQuery;

import java.util.List;


@Service
public class GetApplicationsQueryHandler implements QueryHandler<GetApplicationsQuery, ResponseEntity<List<ApplicationDTO>>>{

   private ApplicationRepository applicationRepository;
   private ApplicationMapper applicationMapper;

   public GetApplicationsQueryHandler(ApplicationRepository applicationRepository, ApplicationMapper applicationMapper) {
      this.applicationRepository = applicationRepository;
      this.applicationMapper = applicationMapper;
   }

   @IgrpQueryHandler
   public ResponseEntity<List<ApplicationDTO>> handle(GetApplicationsQuery applicationsQuery) {
      Specification<Application> spec = buildSpecification(applicationsQuery.getName(), applicationsQuery.getCode());
      List<ApplicationDTO> applications = applicationRepository.findAll(spec)
              .stream()
              .map(applicationMapper::toDto)
              .toList();
      return ResponseEntity.ok(applications);
   }

   private Specification<Application> buildSpecification(final String code, final String name) {
      Specification<Application> spec = Specification.where(null);
      if (code != null && !code.isEmpty()) {
         spec = spec.and((root, query, cb) ->
                 cb.equal(cb.lower(root.get("code")), code.toLowerCase())
         );
      }
      if (name != null && !name.isEmpty()) {
         spec = spec.and((root, query, cb) ->
                 cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%")
         );
      }
      return spec;
   }

}