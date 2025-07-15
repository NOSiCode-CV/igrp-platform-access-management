package cv.igrp.platform.access_management.shared.infrastructure.persistence.entity;

import cv.igrp.platform.access_management.shared.config.AuditEntity;
import cv.igrp.framework.stereotype.IgrpEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class MenuEntryEntity extends AuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    private Integer id;

  
    @NotBlank(message = "name is mandatory")
    @Column(name="name", nullable = false)
    private String name;

  
    @NotNull(message = "type is mandatory")
    @Enumerated(EnumType.STRING)
    @Column(name="type", nullable = false)
    private MenuEntryType type;

  
    @NotNull(message = "position is mandatory")
    @Column(name="position", nullable = false)
    private short position;

  
    @Column(name="icon")
    private String icon;

  
    @NotNull(message = "status is mandatory")
    @Enumerated(EnumType.STRING)
    @Column(name="status", nullable = false)
    private Status status;

  
    @Column(name="target", length=10)
    private String target;

  
    @Column(name="url")
    private String url;

  


  @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "parent_id", referencedColumnName = "id")
    private MenuEntryEntity parentId;
    @NotNull(message = "applicationId is mandatory")


  @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "application_id", referencedColumnName = "id")
    private ApplicationEntity applicationId;   @OneToMany(mappedBy = "menuEntryId")
private List<PermissionEntity> permissions;


}