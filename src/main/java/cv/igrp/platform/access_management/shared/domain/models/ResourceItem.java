package cv.igrp.platform.access_management.shared.domain.models;

import cv.igrp.platform.access_management.shared.config.AuditEntity;
import cv.igrp.framework.stereotype.IgrpEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;
import cv.igrp.platform.access_management.shared.application.constants.ResourceItemType;
import java.util.List;

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

  
    @Enumerated(EnumType.STRING)
    @Column(name="type")
    private ResourceItemType type;

  
    @Column(name="name")
    private String name;

  
    @Column(name="url")
    private String url;

  


  @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "resource", referencedColumnName = "id")
    private Resource resource;   @OneToMany(mappedBy = "Parent")
   private List<MenuEntry> Children;

   @OneToMany(mappedBy = "ResourceItem")
   private List<ResourceItem> MenuEntries;


}