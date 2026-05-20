package cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity;

import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

import java.time.LocalDateTime;

/**
 * Direct permission grant on a {@link ServiceAccountEntity}, bypassing the
 * role layer. Sits alongside {@link ServiceAccountRoleAssignment}: the
 * effective permission set is the union of direct grants and the permissions
 * inherited from the assigned roles.
 */
@Audited
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "t_service_account_permission_grant")
public class ServiceAccountPermissionGrant {

    @EmbeddedId
    private ServiceAccountPermissionId id = new ServiceAccountPermissionId();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("serviceAccountId")
    @JoinColumn(name = "service_account_id")
    private ServiceAccountEntity serviceAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @MapsId("permissionId")
    @JoinColumn(name = "permission_id")
    private PermissionEntity permission;

    @Column(name = "granted_at", nullable = false, updatable = false)
    private LocalDateTime grantedAt = LocalDateTime.now();

    public ServiceAccountPermissionGrant(ServiceAccountEntity serviceAccount,
                                         PermissionEntity permission) {
        this.serviceAccount = serviceAccount;
        this.permission = permission;
        this.id = new ServiceAccountPermissionId(serviceAccount.getId(), permission.getId());
    }
}
