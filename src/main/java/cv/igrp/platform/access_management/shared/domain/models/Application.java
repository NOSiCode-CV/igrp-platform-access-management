package cv.igrp.platform.access_management.shared.domain.models;

import cv.igrp.platform.access_management.shared.config.AuditEntity;
import cv.igrp.framework.stereotype.IgrpEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;
import jakarta.validation.constraints.NotBlank;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.constants.AppType;
import java.util.List;

@Audited
@Getter
@Setter
@ToString
@IgrpEntity
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "t_application")
public class Application extends AuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    private Integer id;

  
    @Column(name="code", unique = true, length=15)
    private String code;

  
    @Column(name="name", length=50)
    private String name;

  

    @NotBlank(message = "description is mandatory")
    @Column(name="description", nullable = false)
    private String description;

  
    @Enumerated(EnumType.STRING)
    @Column(name="status")
    private Status status;

  
    @Enumerated(EnumType.STRING)
    @Column(name="type")
    private AppType type;

  

    @NotBlank(message = "owner is mandatory")
    @Column(name="owner", nullable = false)
    private String owner;

  

    @NotBlank(message = "picture is mandatory")
    @Column(name="picture", nullable = false)
    private String picture;

  

    @NotBlank(message = "url is mandatory")
    @Column(name="url", nullable = false)
    private String url;

  

    @NotBlank(message = "slug is mandatory")
    @Column(name="slug", nullable = false, length=50)
    private String slug;

     @OneToMany(mappedBy = "App")
private List<App> MenuEntries;

   @OneToMany(mappedBy = "ApplicationId")
private List<Application> Departments;


}