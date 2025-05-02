package cv.igrp.platform.access_management.shared.application.constants;

public enum CustomFieldTableName {

    APPLICATION("t_application"),
    RESOURCE("t_resource"),
    ;

    private final String name;

    CustomFieldTableName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
