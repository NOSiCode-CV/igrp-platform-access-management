package cv.igrp.platform.access_management.shared.domain.models;

import cv.igrp.platform.access_management.shared.config.AuditEntity;
import cv.igrp.framework.stereotype.IgrpEntity;
import jakarta.persistence.*;
import lombok.*;
import cv.igrp.platform.access_management.shared.application.constants.MenuEntryType;
import jakarta.validation.constraints.NotBlank;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import jakarta.validation.constraints.NotNull;
import java.util.List;


@Getter
@Setter
@ToString
@IgrpEntity
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "t_menu_entry")
public class MenuEntry extends AuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    private Integer id;

  
    @Column(name="name", length=100)
    private String name;

  
    @Enumerated(EnumType.STRING)
    @Column(name="type")
    private MenuEntryType type;

  
    @Column(name="position")
    private short position;

  

    @NotBlank(message = "icon is mandatory")
    @Column(name="icon", nullable = false)
    private String icon;

  
    @Enumerated(EnumType.STRING)
    @Column(name="status")
    private Status status;

  

    @NotBlank(message = "target is mandatory")
    @Column(name="target", nullable = false, length=10)
    private String target;

  

    @NotBlank(message = "url is mandatory")
    @Column(name="url", nullable = false)
    private String url;

  


  @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", referencedColumnName = "id")
    private Application applicationId;
    @NotNull(message = "resourceId is mandatory")


  @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resource_id", referencedColumnName = "id")
    private Resource resourceId;
    @NotNull(message = "parentId is mandatory")


  @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", referencedColumnName = "id")
    private MenuEntry parentId;   @OneToMany(mappedBy = "ParentId")
   private List<MenuEntry> Menus;

   @OneToMany(mappedBy = "Parent")
   private List<MenuEntry> Self;


}