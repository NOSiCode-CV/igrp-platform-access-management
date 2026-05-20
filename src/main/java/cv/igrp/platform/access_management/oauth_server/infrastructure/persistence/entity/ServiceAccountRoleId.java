package cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Embeddable
public class ServiceAccountRoleId implements Serializable {

    @Column(name = "service_account_id")
    private UUID serviceAccountId;

    @Column(name = "role_id")
    private Integer roleId;
}
