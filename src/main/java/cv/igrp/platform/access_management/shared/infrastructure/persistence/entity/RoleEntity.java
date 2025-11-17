/* THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO. */
/* DO NOT MODIFY IT BECAUSE IT COULD BE REWRITTEN AT ANY TIME. */

package cv.igrp.platform.access_management.shared.infrastructure.persistence.entity;

import cv.igrp.platform.access_management.shared.config.AuditEntity;
import cv.igrp.framework.stereotype.IgrpEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;
import jakarta.validation.constraints.NotBlank;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import java.util.Set;
import java.util.HashSet;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import java.util.ArrayList;
import java.util.List;

@Audited
@Getter
@Setter
@IgrpEntity
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "t_role",
  uniqueConstraints = {
    @UniqueConstraint(
      name = "role_department_uk",
      columnNames = {
        "code","department"
      }
    )
  })
public class RoleEntity extends AuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    private Integer id;

  
    @Column(name="name")
    private String name;

  
    @NotBlank(message = "code is mandatory")
    @Column(name="code", nullable = false)
    private String code;

  
    @Column(name="description")
    private String description;

  
    @Enumerated(EnumType.STRING)
    @Column(name="status")
    private Status status;

  
    @Column(name="icon")
    private String icon;

  


  @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department", referencedColumnName = "id")
    private DepartmentEntity department;


  @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent", referencedColumnName = "id")
    private RoleEntity parent;


  
    @ManyToMany(fetch = FetchType.LAZY, cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    @JoinTable(
            name = "t_role_permission",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission")
    )
    @OnDelete(action = OnDeleteAction.SET_NULL)
private Set<PermissionEntity> permissions = new HashSet<>();


  
    @ManyToMany(fetch = FetchType.LAZY)
private Set<IGRPUserEntity> users = new HashSet<>();
   @OneToMany(mappedBy = "parent")
private List<RoleEntity> children = new ArrayList<>();

   @ManyToMany(mappedBy = "roles", fetch = FetchType.LAZY)
private Set<ApplicationEntity> applications = new HashSet<>();


}