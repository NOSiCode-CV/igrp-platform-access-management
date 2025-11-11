/* THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO. */
/* DO NOT MODIFY IT BECAUSE IT COULD BE REWRITTEN AT ANY TIME. */

package cv.igrp.platform.access_management.shared.infrastructure.persistence.entity;

import cv.igrp.platform.access_management.shared.config.AuditEntity;
import cv.igrp.framework.stereotype.IgrpEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.constants.AppType;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;

@Audited
@Getter
@Setter
@IgrpEntity
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "t_application")
public class ApplicationEntity extends AuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    private Integer id;

  
    @NotBlank(message = "code is mandatory")
    @Column(name="code", nullable = false, length=60)
    private String code;

  
    @NotBlank(message = "name is mandatory")
    @Column(name="name", nullable = false, length=50)
    private String name;

  
    @Column(name="description")
    private String description;

  
    @NotNull(message = "status is mandatory")
    @Enumerated(EnumType.STRING)
    @Column(name="status", nullable = false)
    private Status status;

  
    @NotNull(message = "type is mandatory")
    @Enumerated(EnumType.STRING)
    @Column(name="type", nullable = false)
    private AppType type;

  
    @Column(name="owner")
    private String owner;

  
    @Column(name="picture")
    private String picture;

  
    @Column(name="url")
    private String url;

  
    @Column(name="slug", length=50)
    private String slug;

  


  
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "t_application_resource",
            joinColumns = @JoinColumn(name = "application_id"),
            inverseJoinColumns = @JoinColumn(name = "resource_id")
    )
private Set<ResourceEntity> resources = new HashSet<>();


  
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "t_application_role",
            joinColumns = @JoinColumn(name = "application_id"),
            inverseJoinColumns = @JoinColumn(name = "roles")
    )
private Set<RoleEntity> roles = new HashSet<>();   @OneToMany(mappedBy = "applicationId")
private List<MenuEntryEntity> menus = new ArrayList<>();

   @ManyToMany(mappedBy = "applications", fetch = FetchType.LAZY)
private Set<DepartmentEntity> departments = new HashSet<>();


}