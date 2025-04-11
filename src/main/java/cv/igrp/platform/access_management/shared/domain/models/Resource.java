package cv.igrp.platform.access_management.shared.domain.models;

import cv.igrp.platform.access_management.shared.config.AuditEntity;
import cv.igrp.framework.stereotype.IgrpEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;
import cv.igrp.platform.access_management.shared.application.constants.ResourceType;
import java.util.List;

@Audited
@Getter
@Setter
@ToString
@IgrpEntity
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "t_resource")
public class Resource extends AuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    private Integer id;

  
    @Column(name="name", length=100)
    private String name;

  
    @Column(name="type", length=15)
    private String type;

  
    @Enumerated(EnumType.STRING)
    @Column(name="status")
    private ResourceType status;

  


  @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", referencedColumnName = "id")
    private Application applicationId;   @OneToMany(mappedBy = "ResourceId")
   private List<Resource> Resource;


}