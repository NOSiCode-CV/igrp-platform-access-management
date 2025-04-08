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
@Table(name = "t_profile")
public class Profile extends AuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "username", unique = true, nullable = false)
    private String username;

  

    @NotBlank(message = "email is mandatory")
    @Column(name="email", nullable = false)
    private String email;

  

    @NotBlank(message = "signatureUrl is mandatory")
    @Column(name="signatureurl", nullable = false)
    private String signatureUrl;

  

    @NotBlank(message = "pictureUrl is mandatory")
    @Column(name="pictureurl", nullable = false)
    private String pictureUrl;

  

    @NotBlank(message = "fullname is mandatory")
    @Column(name="fullname", nullable = false)
    private String fullname;

  
}