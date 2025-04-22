package cv.igrp.platform.access_management.department.application.queries.queries;

import cv.igrp.framework.core.domain.Query;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor

public class GetDepartmentsQuery implements Query {

    @NotNull(message = "The field <applicationId> is required.")
    private Integer applicationId;

    @NotNull(message = "The field <departmentId> is required.")
    private Integer departmentId;

    @NotBlank(message = "The field <name> is required.")
    private String name;

    @NotBlank(message = "The field <code> is required.")
    private String code;

}