/* THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO. */
/* DO NOT MODIFY IT BECAUSE IT COULD BE REWRITTEN AT ANY TIME. */

package cv.igrp.platform.access_management.shared.infrastructure.persistence.entity;

import cv.igrp.platform.access_management.shared.config.AuditEntity;
import cv.igrp.framework.stereotype.IgrpEntity;
import jakarta.persistence.*;
import lombok.*;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import java.util.HashSet;


@Getter
@Setter
@IgrpEntity
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "t_favorite_application")
public class FavoriteApplicationEntity extends AuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    private Integer id;

  
    @Column(name="userid")
    private Integer userId;

  
    @NotNull(message = "application is mandatory")


  
    @ManyToMany(fetch = FetchType.LAZY)
private Set<ApplicationEntity> applications = new HashSet<>();

}