/* THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO. */
/* DO NOT MODIFY IT BECAUSE IT COULD BE REWRITTEN AT ANY TIME. */

package cv.igrp.platform.access_management.shared.infrastructure.persistence.entity;

import cv.igrp.framework.stereotype.IgrpEntity;
import cv.igrp.platform.access_management.shared.application.constants.ResourceType;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.config.AuditEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.envers.Audited;

import java.util.*;

@Audited
@Getter
@Setter
@IgrpEntity
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "t_resource")
@ToString(onlyExplicitlyIncluded = true)
public class ResourceEntity extends AuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    @ToString.Include
    private Integer id;


    @NotBlank(message = "name is mandatory")
    @Column(name = "name", nullable = false, length = 100)
    private String name;


    @NotBlank(message = "description is mandatory")
    @Column(name = "description", nullable = false)
    private String description;


    @NotNull(message = "type is mandatory")
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private ResourceType type;


    @Column(name = "external_id")
    private String externalId;


    @NotNull(message = "status is mandatory")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;


    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "t_resource_permission",
            joinColumns = @JoinColumn(name = "resource_id"),
            inverseJoinColumns = @JoinColumn(name = "permission")
    )
    private Set<PermissionEntity> permissions = new HashSet<>();
    @OneToMany(mappedBy = "resourceId")
    private List<ResourceItemEntity> items = new ArrayList<>();

    @ManyToMany(mappedBy = "resources", fetch = FetchType.LAZY)
    private Set<ApplicationEntity> applications = new HashSet<>();

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof ResourceEntity that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}