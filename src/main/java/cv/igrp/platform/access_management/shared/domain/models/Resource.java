package cv.igrp.platform.access_management.shared.domain.models;

import cv.igrp.platform.access_management.shared.config.AuditEntity;
import cv.igrp.framework.stereotype.IgrpEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import cv.igrp.platform.access_management.shared.application.constants.ResourceType;
import cv.igrp.platform.access_management.shared.application.constants.Status;

import java.util.ArrayList;
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

  

    @NotBlank(message = "name is mandatory")
    @Column(name="name", nullable = false, length=100)
    private String name;

  
    @NotNull(message = "type is mandatory")
    @Enumerated(EnumType.STRING)
    @Column(name="type", nullable = false)
    private ResourceType type;

  
    @NotNull(message = "status is mandatory")
    @Enumerated(EnumType.STRING)
    @Column(name="status", nullable = false)
    private Status status;

  
    @NotNull(message = "applicationId is mandatory")


  @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "application_id", referencedColumnName = "id")
    private Application applicationId;

    @Column(name="external_id")
    private String externalId;

     @OneToMany(mappedBy = "resourceId", cascade = CascadeType.ALL, orphanRemoval = true)
private List<ResourceItem> items;

   @OneToMany(mappedBy = "resourceId")
private List<MenuEntry> menus;


}