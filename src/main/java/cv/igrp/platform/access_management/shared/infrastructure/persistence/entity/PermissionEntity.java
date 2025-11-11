/* THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO. */
/* DO NOT MODIFY IT BECAUSE IT COULD BE REWRITTEN AT ANY TIME. */

package cv.igrp.platform.access_management.shared.infrastructure.persistence.entity;

import cv.igrp.platform.access_management.shared.config.AuditEntity;
import cv.igrp.framework.stereotype.IgrpEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import java.util.HashSet;
import java.util.Set;

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

  


  @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department", referencedColumnName = "id")
    private DepartmentEntity department;   @ManyToMany(mappedBy = "permissions", fetch = FetchType.LAZY)
private Set<ResourceEntity> resources = new HashSet<>();

   @ManyToMany(mappedBy = "permissions", fetch = FetchType.LAZY)
private Set<ResourceEntity> resources = new HashSet<>();


}