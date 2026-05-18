package cv.igrp.platform.access_management.app.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.app.mapper.ApplicationMapper;
import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.ApplicationDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpErrorCode;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;


/**
 * Command handler responsible for updating an existing {@link ApplicationEntity} entity.
 * <p>
 * This handler receives an {@link UpdateApplicationCommand}, retrieves the target application
 * by ID, updates its fields with the provided data, persists the changes, and returns the updated
 * application as a {@link ApplicationDTO}.
 * </p>
 *
 * <p>
 * If no application is found for the given ID, an {@link IgrpResponseStatusException} is thrown with
 * HTTP status {@code 404 NOT_FOUND}.
 * </p>
 *
 * @see UpdateApplicationCommand
 * @see ApplicationEntityRepository
 * @see ApplicationMapper
 * @see ApplicationDTO
 */
@Component
public class UpdateApplicationCommandHandler implements CommandHandler<UpdateApplicationCommand, ResponseEntity<ApplicationDTO>> {

    private final ApplicationEntityRepository applicationRepository;
    private final ApplicationMapper applicationMapper;
    private final DepartmentEntityRepository departmentEntityRepository;

    /**
     * Constructs a new {@code UpdateApplicationCommandHandler} with the required dependencies.
     *
     * @param applicationRepository the repository used to retrieve and persist {@link ApplicationEntity} entities
     * @param applicationMapper     the mapper used to convert between {@link ApplicationEntity} and {@link ApplicationDTO}
     */
    public UpdateApplicationCommandHandler(ApplicationEntityRepository applicationRepository, ApplicationMapper applicationMapper, DepartmentEntityRepository departmentEntityRepository) {
        this.applicationRepository = applicationRepository;
        this.applicationMapper = applicationMapper;
        this.departmentEntityRepository = departmentEntityRepository;
    }

    /**
     * Handles the update of an {@link ApplicationEntity} based on the data provided in the {@link UpdateApplicationCommand}.
     * <ul>
     *     <li>Retrieves the application by ID.</li>
     *     <li>Updates the entity's fields with values from the {@link ApplicationDTO}.</li>
     *     <li>Persists the updated entity and returns the result as a DTO.</li>
     * </ul>
     *
     * @param command the command containing the application ID and updated data
     * @return a {@link ResponseEntity} containing the updated {@link ApplicationDTO}
     * @throws IgrpResponseStatusException if the application is not found
     */
    @IgrpCommandHandler
    public ResponseEntity<ApplicationDTO> handle(UpdateApplicationCommand command) {

        ApplicationDTO appDto = command.getApplicationdto();

        ApplicationEntity application = applicationRepository.findByCodeAndStatusNot(command.getCode(), Status.DELETED)
                .orElseThrow(() -> IgrpResponseStatusException.of(IgrpErrorCode.IGRP_AUTH_APPLICATION_NOT_FOUND_BY_CODE, command.getCode()));

        application.setName(appDto.getName());
        application.setDescription(appDto.getDescription());
        application.setStatus(appDto.getStatus());
        application.setType(appDto.getType());
        application.setOwner(appDto.getOwner());
        application.setPicture(appDto.getPicture());
        application.setUrl(appDto.getUrl() != null ? appDto.getUrl().toString() : null);
        application.setSlug(appDto.getSlug());

        for (var dept : appDto.getDepartments()) {

            var department = departmentEntityRepository.findByCodeAndStatusNot(dept, DepartmentStatus.DELETED)
                    .orElseThrow(() -> IgrpResponseStatusException.of(IgrpErrorCode.IGRP_AUTH_DEPARTMENT_NOT_FOUND_BY_CODE, dept));

            var parent = department.getParentId();

            if (parent != null) {
                var parentApplications = parent.getApplications();
                if (parentApplications != null) {
                    if (parentApplications.stream().noneMatch(app -> app.getId().equals(application.getId())))
                        throw IgrpResponseStatusException.of(IgrpErrorCode.IGRP_AUTH_DEPARTMENT_ACCESS_DENIED, "Department with code %s cannot be granted access to application %s because its parent department does not have access.".formatted(dept, application.getCode()));
                }
            }

            application.getDepartments().add(department);
        }

        ApplicationEntity updatedApplication = applicationRepository.save(application);

        return ResponseEntity.ok(applicationMapper.toDto(updatedApplication));
    }

}