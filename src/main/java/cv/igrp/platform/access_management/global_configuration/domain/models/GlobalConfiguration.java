package cv.igrp.platform.access_management.global_configuration.domain.models;

import cv.igrp.platform.access_management.shared.config.AuditEntity;
import cv.igrp.framework.stereotype.IgrpEntity;
import jakarta.persistence.*;
import lombok.*;
import cv.igrp.platform.access_management.global_configuration.application.constants.GlobalConfigurationType;


@Getter
@Setter
@ToString
@IgrpEntity
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "t_global_configuration")
public class GlobalConfiguration extends AuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    private Integer id;

  
    @Lob
    @Column(name="config", columnDefinition="TEXT")
    private String config;

  
    @Enumerated(EnumType.STRING)
    @Column(name="type")
    private GlobalConfigurationType type;

  
}