package cv.igrp.platform.access_management.users.application.commands.commands;

import cv.igrp.framework.core.domain.Command;

public class GetIGRPUserByIdCommand implements Command {
    private Long id; // vindo do PathVariable
    private Long applicationId;
    private Long departmentId;
    private String name;
    private String username;
    private String email;

    public GetIGRPUserByIdCommand() {}

    public GetIGRPUserByIdCommand(Long id, Long applicationId, Long departmentId,
                                   String name, String username, String email) {
        this.id = id;
        this.applicationId = applicationId;
        this.departmentId = departmentId;
        this.name = name;
        this.username = username;
        this.email = email;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
