package cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity;

import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Machine principal backed by one OAuth client. The OAuth client owns how a
 * machine authenticates; this entity owns what that machine is allowed to do.
 */
@Audited
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "t_service_account", indexes = {
        @Index(name = "idx_service_account_oauth_client", columnList = "oauth_client_id", unique = true),
        @Index(name = "idx_service_account_active", columnList = "active")
})
public class ServiceAccountEntity {

    @Id
    @Column(name = "id", unique = true, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 180)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @JoinColumn(name = "oauth_client_id", nullable = false, unique = true)
    private OAuthClientEntity oauthClient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id")
    private ApplicationEntity application;

    @OneToMany(mappedBy = "serviceAccount", fetch = FetchType.LAZY,
            cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ServiceAccountRoleAssignment> roleAssignments = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Set<RoleEntity> getRoles() {
        LocalDateTime now = LocalDateTime.now();
        return roleAssignments.stream()
                .filter(assignment -> assignment.getExpiresAt() == null
                        || assignment.getExpiresAt().isAfter(now))
                .map(ServiceAccountRoleAssignment::getRole)
                .collect(Collectors.toSet());
    }

    public void replaceRoleAssignments(Set<RoleEntity> roles) {
        roleAssignments.clear();
        if (roles == null) {
            return;
        }
        roles.forEach(role -> roleAssignments.add(new ServiceAccountRoleAssignment(this, role, null)));
    }
}
