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

@Audited
@Getter
@Setter
@ToString
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

  
    @Column(name="name", length=60)
    private String name;

  
    @NotBlank(message = "description is mandatory")
    @Column(name="description", nullable = false)
    private String description;

  
    @Enumerated(EnumType.STRING)
    @Column(name="status")
    private Status status;

  


  @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "application", referencedColumnName = "id")
    private ApplicationEntity application;


  
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "department", unique = true, referencedColumnName = "id")
    private DepartmentEntity department;


  @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "menu_entry_id", referencedColumnName = "id")
    private MenuEntryEntity menuEntryId;   @ManyToMany(mappedBy = "permissions", fetch = FetchType.EAGER)
private Set<RoleEntity> roles;


}