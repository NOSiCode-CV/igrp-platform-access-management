package cv.igrp.platform.access_management.department.application.queries.queries;

import cv.igrp.framework.core.domain.Query;



public class GetDepartmentsQuery implements Query {

    private Integer applicationId;
    private String name;
    private String status;

    public GetDepartmentsQuery() {
    }

    public GetDepartmentsQuery(Integer applicationId, String name, String status) {
        this.applicationId = applicationId;
        this.name = name;
        this.status = status;
    }

    public Integer getApplicationId() {
        return applicationId;
    }

    public String getName() {
        return name;
    }

    public String getStatus() {
        return status;
    }
}
