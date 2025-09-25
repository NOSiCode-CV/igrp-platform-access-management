package cv.igrp.platform.access_management.app.application.commands;

import cv.igrp.framework.core.domain.Command;
import cv.igrp.platform.access_management.shared.application.dto.CodeListRequestDTO;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RemoveDepartmentFromApplicationCommand implements Command {


    private CodeListRequestDTO codelistrequestdto;
    @NotBlank(message = "The field <code> is required")
    private String code;

}