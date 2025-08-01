package cv.igrp.platform.access_management.shared.infrastructure.persistence.entity;

import cv.igrp.platform.access_management.shared.config.AuditEntity;
import cv.igrp.framework.stereotype.IgrpEntity;
import cv.igrp.framework.auth.core.model.UserIdentity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Audited
@Getter
@Setter
@ToString
@IgrpEntity
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "t_user")
public class IGRPUserEntity extends AuditEntity implements UserIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    private Integer id;

    @NotBlank(message = "name is mandatory")
    @Column(name="name", nullable = false)
    private String name;

    @Column(name="username", unique = true)
    private String username;

    @Column(name="email", unique = true)
    private String email;

    @Column(name = "external_id", unique = true)
    private String externalId;

    @Column(name = "email_verified")
    private boolean emailVerified = false;

    @ManyToMany(mappedBy = "users", fetch = FetchType.EAGER)
    private List<RoleEntity> roles;

    @ElementCollection
    @CollectionTable(name = "t_user_custom_fields", joinColumns = @JoinColumn(name = "user_id"))
    @MapKeyColumn(name = "field_key")
    @Column(name = "field_value")
    private Map<String, String> customFields;

    // Implementação da interface UserIdentity

    @Override
    public String getId() {
        return id != null ? id.toString() : null;
    }

    @Override
    public String getUsername() {
        return this.username;
    }

    @Override
    public String getFirstName() {
        return this.name; // adaptar se tiver `firstName` e `lastName` separadamente
    }

    @Override
    public String getLastName() {
        return ""; // ou adaptar conforme necessidade
    }

    @Override
    public String getEmail() {
        return this.email;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String getExternalId() {
        return this.externalId;
    }

    @Override
    public boolean isEmailVerified() {
        return this.emailVerified;
    }
}