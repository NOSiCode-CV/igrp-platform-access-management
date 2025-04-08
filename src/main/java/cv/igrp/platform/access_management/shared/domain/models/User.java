package cv.igrp.platform.access_management.shared.domain.models;

import cv.igrp.platform.access_management.shared.config.AuditEntity;
import cv.igrp.framework.stereotype.IgrpEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;
import jakarta.validation.constraints.NotBlank;

@Audited
@Getter
@Setter
@ToString
@IgrpEntity
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "t_user")
public class User extends AuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "igrpusername", unique = true, nullable = false)
    private String igrpUsername;

  

    @NotBlank(message = "igrpPassword is mandatory")
    @Column(name="igrppassword", nullable = false)
    private String igrpPassword;

  

    @NotBlank(message = "image is mandatory")
    @Column(name="image", nullable = false)
    private String image;

  
}