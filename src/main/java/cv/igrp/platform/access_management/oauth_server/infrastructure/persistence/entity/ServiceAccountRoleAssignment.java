package cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity;

import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
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

import java.time.LocalDateTime;

@Audited
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "t_service_account_role_assignment")
public class ServiceAccountRoleAssignment {

    @EmbeddedId
    private ServiceAccountRoleId id = new ServiceAccountRoleId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("serviceAccountId")
    @JoinColumn(name = "service_account_id")
    private ServiceAccountEntity serviceAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("roleId")
    @JoinColumn(name = "role_id")
    private RoleEntity role;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private LocalDateTime assignedAt = LocalDateTime.now();

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    public ServiceAccountRoleAssignment(ServiceAccountEntity serviceAccount,
                                        RoleEntity role,
                                        LocalDateTime expiresAt) {
        this.serviceAccount = serviceAccount;
        this.role = role;
        this.expiresAt = expiresAt;
        this.id = new ServiceAccountRoleId(serviceAccount.getId(), role.getId());
    }
}
