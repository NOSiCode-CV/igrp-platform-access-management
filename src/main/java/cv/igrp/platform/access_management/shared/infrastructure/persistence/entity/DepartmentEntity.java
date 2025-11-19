/* THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO. */
/* DO NOT MODIFY IT BECAUSE IT COULD BE REWRITTEN AT ANY TIME. */

package cv.igrp.platform.access_management.shared.infrastructure.persistence.entity;

import cv.igrp.platform.access_management.shared.config.AuditEntity;
import cv.igrp.framework.stereotype.IgrpEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;
import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
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
@Table(name = "t_department")
public class DepartmentEntity extends AuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    private Integer id;

  
    @Column(name="code")
    private String code;

  
    @Column(name="name")
    private String name;

  
    @Column(name="description")
    private String description;

  
    @Enumerated(EnumType.STRING)
    @Column(name="status")
    private DepartmentStatus status;

  
    @Column(name="icon")
    private String icon;

  


  @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", referencedColumnName = "id")
    private DepartmentEntity parentId;


  
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "t_department_application",
            joinColumns = @JoinColumn(name = "department_id"),
            inverseJoinColumns = @JoinColumn(name = "application_id")
    )
private Set<ApplicationEntity> applications = new HashSet<>();   @OneToMany(mappedBy = "parentId")
private List<DepartmentEntity> childrenids = new ArrayList<>();

   @ManyToMany(mappedBy = "departments", fetch = FetchType.LAZY)
private Set<PermissionEntity> permissions = new HashSet<>();

   @OneToMany(mappedBy = "department")
private List<RoleEntity> roles = new ArrayList<>();

   @ManyToMany(mappedBy = "departments", fetch = FetchType.LAZY)
private Set<MenuEntryEntity> menuentries = new HashSet<>();

   @ManyToMany(mappedBy = "departments", fetch = FetchType.LAZY)
private Set<ResourceEntity> resources = new HashSet<>();


}