package cv.igrp.platform.access_management.shared.domain.models;

import cv.igrp.platform.access_management.shared.config.AuditEntity;
import cv.igrp.framework.stereotype.IgrpEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;
import jakarta.validation.constraints.NotBlank;

@Audited
@Getter
@Setter
@ToString
@IgrpEntity
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "t_permission")
public class Permission extends AuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "permission", unique = true, nullable = false)
    private String permission;

  

    @NotBlank(message = "name is mandatory")
    @Column(name="name", nullable = false)
    private String name;

  

    @NotBlank(message = "description is mandatory")
    @Column(name="description", nullable = false)
    private String description;

  
}