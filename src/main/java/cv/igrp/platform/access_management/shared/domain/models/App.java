package cv.igrp.platform.access_management.shared.domain.models;

import cv.igrp.platform.access_management.shared.config.AuditEntity;
import cv.igrp.framework.stereotype.IgrpEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;
import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import cv.igrp.platform.access_management.shared.application.constants.AppType;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import java.util.List;

@Audited
@Getter
@Setter
@ToString
@IgrpEntity
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "t_app")
public class App extends AuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    private Integer id;

  
    @Column(name="uuid", unique = true)
    private UUID uuid;

  
    @Column(name="owner")
    private String owner;

  
    @Column(name="name")
    private String name;

  

    @NotBlank(message = "description is mandatory")
    @Column(name="description", nullable = false)
    private String description;

  
    @Enumerated(EnumType.STRING)
    @Column(name="type")
    private AppType type;

  

    @NotBlank(message = "url is mandatory")
    @Column(name="url", nullable = false)
    private String url;

  

    @NotBlank(message = "slug is mandatory")
    @Column(name="slug", nullable = false)
    private String slug;

  
    @Column(name="code", unique = true)
    private String code;

  
    @Column(name="userpermissions")
    private String userPermissions;

  
    @Enumerated(EnumType.STRING)
    @Column(name="status")
    private Status status;

  

    @NotBlank(message = "picture is mandatory")
    @Column(name="picture", nullable = false)
    private String picture;

     @OneToMany(mappedBy = "App")
   private List<App> MenuEntries;


}