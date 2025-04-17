package cv.igrp.platform.access_management.department.application.commands.commands;

import cv.igrp.framework.core.domain.Command;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RemoveRolesFromDepartmentCommand implements Command {
    @NotNull private Integer departmentId;
    private List<Integer> roleIds;
}