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
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import java.util.List;

@Audited
@Getter
@Setter
@IgrpEntity
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "t_role")
public class RoleEntity extends AuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    private Integer id;

  
    @NotBlank(message = "name is mandatory")
    @Column(name="name", nullable = false)
    private String name;

  
    @Column(name="description")
    private String description;

  
    @Enumerated(EnumType.STRING)
    @Column(name="status")
    private Status status;

  


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
private Set<PermissionEntity> permissions;


  
    @ManyToMany(fetch = FetchType.LAZY)
private Set<IGRPUserEntity> users;
   @OneToMany(mappedBy = "parent")
private List<RoleEntity> children;


}