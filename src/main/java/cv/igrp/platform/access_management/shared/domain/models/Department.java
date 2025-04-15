package cv.igrp.platform.access_management.shared.domain.models;

import cv.igrp.platform.access_management.shared.config.AuditEntity;
import cv.igrp.framework.stereotype.IgrpEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;
import java.time.LocalDate;
import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;

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

  
    @Column(name="departmentname")
    private String departmentName;

  
    @Column(name="code")
    private String code;

  
    @Column(name="archivedat")
    private LocalDate archivedAt;

  
    @Enumerated(EnumType.STRING)
    @Column(name="status")
    private DepartmentStatus status;

  


  @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "application", referencedColumnName = "id")
    private Application application;
}