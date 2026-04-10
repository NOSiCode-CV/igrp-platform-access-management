/* THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO. */
/* DO NOT MODIFY IT BECAUSE IT COULD BE REWRITTEN AT ANY TIME. */

package cv.igrp.platform.access_management.shared.infrastructure.persistence.entity;

import cv.igrp.platform.access_management.shared.config.AuditEntity;
import cv.igrp.framework.stereotype.IgrpEntity;
import jakarta.persistence.*;
import lombok.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import cv.igrp.platform.access_management.shared.application.constants.InvitationStatus;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.HashSet;
import cv.igrp.platform.access_management.shared.application.constants.IdentifierType;


@Getter
@Setter
@IgrpEntity
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "t_invitation_entity")
public class InvitationEntity extends AuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    private Integer id;

    @NotNull(message = "identifierType is mandatory")
    @Enumerated(EnumType.STRING)
    @Column(name="identifier_type", nullable = false)
    private IdentifierType identifierType;

    @NotBlank(message = "identifierValue is mandatory")
    @Column(name="identifier_value", nullable = false)
    private String identifierValue;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "t_invitation_auth_methods", joinColumns = @JoinColumn(name = "invitation_id"))
    @Column(name = "auth_method")
    private Set<String> allowedAuthMethods = new HashSet<>();

  
    @NotNull(message = "status is mandatory")
    @Enumerated(EnumType.STRING)
    @Column(name="status", nullable = false)
    private InvitationStatus status;

  
    @NotBlank(message = "token is mandatory")
    @Column(name="token", unique = true, nullable = false)
    private String token;

  
    @Column(name="expiry")
    private LocalDateTime expiry;

  
    @Column(name="comments")
    private String comments;

  


  
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "t_invitation_roles",
            joinColumns = @JoinColumn(name = "invitation_id"),
            inverseJoinColumns = @JoinColumn(name = "invitation")
    )
private Set<RoleEntity> roles = new HashSet<>();
}