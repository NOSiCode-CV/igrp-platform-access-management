package cv.igrp.platform.access_management.department.mapper;

import cv.igrp.platform.access_management.shared.application.dto.DepartmentDTO;
import cv.igrp.platform.access_management.shared.domain.models.Application;
import cv.igrp.platform.access_management.shared.domain.models.Department;
import org.springframework.stereotype.Component;

import static java.util.Optional.ofNullable;

@Component
public class DepartmentMapper {

    public DepartmentDTO toDto(Department department) {
        if (department == null) return null;
        DepartmentDTO dto = new DepartmentDTO();
        dto.setId(department.getId());
        dto.setCode(department.getCode());
        dto.setName(department.getName());
        dto.setDescription(department.getDescription());
        dto.setApplication_id(ofNullable(department.getApplicationId()).map(Application::getId).orElse(null));
        dto.setParent_id(ofNullable(department.getParentId()).map(Department::getId).orElse(null));
        return dto;
    }

    public Department toEntity(DepartmentDTO dto) {
        if (dto == null) return null;
        Department department = new Department();
        department.setId(dto.getId());
        department.setCode(dto.getCode());
        department.setName(dto.getName());
        department.setStatus(dto.getStatus());
        department.setDescription(dto.getDescription());
        return department;
    }

    public void updateEntityFromDto(DepartmentDTO dto, Department department) {
        department.setCode(dto.getCode());
        department.setName(dto.getName());
        department.setDescription(dto.getDescription());
    }
}