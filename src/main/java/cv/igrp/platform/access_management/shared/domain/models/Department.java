package cv.igrp.platform.access_management.shared.domain.models;

import cv.igrp.platform.access_management.shared.config.AuditEntity;
import cv.igrp.framework.stereotype.IgrpEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;
import jakarta.validation.constraints.NotBlank;
import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import java.util.List;

@Audited
@Getter
@Setter
@ToString
@IgrpEntity
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "t_department")
public class Department extends AuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    private Integer id;

  
    @Column(name="code")
    private String code;

  
    @Column(name="name")
    private String name;

  

    @NotBlank(message = "description is mandatory")
    @Column(name="description", nullable = false)
    private String description;

  
    @Enumerated(EnumType.STRING)
    @Column(name="status")
    private DepartmentStatus status;

  


  @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "application_id", referencedColumnName = "id")
    private Application applicationId;


  @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", referencedColumnName = "id")
    private Department parentId;   @OneToMany(mappedBy = "parentId")
private List<Department> childrenids;

   @OneToMany(mappedBy = "department")
private List<Role> roles;


}