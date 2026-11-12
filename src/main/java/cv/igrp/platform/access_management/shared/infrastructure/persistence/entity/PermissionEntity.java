/* THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO. */
/* DO NOT MODIFY IT BECAUSE IT COULD BE REWRITTEN AT ANY TIME */

package cv.igrp.platform.access_management.shared.infrastructure.persistence.entity;

import cv.igrp.platform.access_management.shared.config.AuditEntity;
import cv.igrp.framework.stereotype.IgrpEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import java.util.Set;
import java.util.HashSet;

@Audited
@Getter
@Setter
@IgrpEntity
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "t_permission")
public class PermissionEntity extends AuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    private Integer id;

  
    @Column(name="name")
    private String name;

  
    @Column(name="description")
    private String description;

  
    @Enumerated(EnumType.STRING)
    @Column(name="status")
    private Status status;

    /** v3-02: the route key, {service}.{resource path}.{action}; immutable; null for legacy igrp.* permissions. */
    // Immutable for JPA: only a service rename (#11) rewrites it, through JDBC in RouteRegistrationService.
    @Column(name = "permission_key", length = 255, unique = true, updatable = false)
    private String permissionKey;

    /** v3-02: the service that registered the key. */
    @Column(name = "service", length = 120)
    private String service;

    /** v3-02: the application of the client that registered the key. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id")
    private ApplicationEntity application;

    /** v3-02: the newest service version that reported the key. Not audited: it changes on every deploy. */
    @NotAudited
    @Column(name = "last_seen_version", length = 64)
    private String lastSeenVersion;

  


  
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "t_permission_department",
            joinColumns = @JoinColumn(name = "permission_id"),
            inverseJoinColumns = @JoinColumn(name = "department")
    )
private Set<DepartmentEntity> departments = new HashSet<>();   @ManyToMany(mappedBy = "permissions", fetch = FetchType.LAZY)
private Set<ResourceEntity> resources = new HashSet<>();


}