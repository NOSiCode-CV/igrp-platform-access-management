package cv.igrp.platform.access_management.shared.domain.models;

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
public class Permission extends AuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
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

  


  @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application", referencedColumnName = "id")
    private Application application;   @ManyToMany(mappedBy = "permissions", fetch = FetchType.LAZY)
private Set<Role> ;


}