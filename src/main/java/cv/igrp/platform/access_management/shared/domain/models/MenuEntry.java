package cv.igrp.platform.access_management.shared.domain.models;

import cv.igrp.platform.access_management.shared.config.AuditEntity;
import cv.igrp.framework.stereotype.IgrpEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;
import jakarta.validation.constraints.NotBlank;
import cv.igrp.platform.access_management.shared.application.constants.MenuEntryType;
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
@Table(name = "t_menu_entry")
public class MenuEntry extends AuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    private Integer id;

  

    @NotBlank(message = "position is mandatory")
    @Column(name="position", nullable = false)
    private String position;

  
    @Enumerated(EnumType.STRING)
    @Column(name="type")
    private MenuEntryType type;

  
    @Column(name="name")
    private String name;

  

    @NotBlank(message = "icon is mandatory")
    @Column(name="icon", nullable = false)
    private String icon;

  
    @Enumerated(EnumType.STRING)
    @Column(name="status")
    private Status status;

  

    @NotBlank(message = "target is mandatory")
    @Column(name="target", nullable = false)
    private String target;

  

    @NotBlank(message = "userPermissions is mandatory")
    @Column(name="userpermissions", nullable = false)
    private String userPermissions;

  

    @NotBlank(message = "url is mandatory")
    @Column(name="url", nullable = false)
    private String url;

  


  @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "resourceitem", referencedColumnName = "id")
    private ResourceItem resourceItem;


  @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "parent", referencedColumnName = "id")
    private MenuEntry parent;


  @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "app", referencedColumnName = "id")
    private App app;   @OneToMany(mappedBy = "Parent")
   private List<MenuEntry> Children;

   @OneToMany(mappedBy = "Parent")
   private List<MenuEntry> Self;


}