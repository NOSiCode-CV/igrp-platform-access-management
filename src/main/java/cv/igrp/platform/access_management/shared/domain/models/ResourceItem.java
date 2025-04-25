package cv.igrp.platform.access_management.shared.domain.models;

import cv.igrp.platform.access_management.shared.config.AuditEntity;
import cv.igrp.framework.stereotype.IgrpEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Audited
@Getter
@Setter
@ToString
@IgrpEntity
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "t_resource_item")
public class ResourceItem extends AuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    private Integer id;

  

    @NotBlank(message = "name is mandatory")
    @Column(name="name", nullable = false, length=100)
    private String name;

  
    @Column(name="url")
    private String url;

  
    @NotNull(message = "resourceId is mandatory")


  @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resource_id", referencedColumnName = "id")
    private Resource resourceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "permission_id", referencedColumnName = "id")
    private Permission permissionId;

}