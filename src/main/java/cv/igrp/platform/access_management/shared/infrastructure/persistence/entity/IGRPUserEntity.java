package cv.igrp.platform.access_management.shared.infrastructure.persistence.entity;

import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.config.AuditEntity;
import cv.igrp.framework.stereotype.IgrpEntity;
import cv.igrp.framework.auth.core.model.UserIdentity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.envers.Audited;
import org.hibernate.type.SqlTypes;
import jakarta.validation.constraints.NotBlank;

import java.util.*;

@Audited
@Getter
@Setter
@ToString(exclude = {"userRoleAssignments"})
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

    @Column(name="name")
    private String name;

    // 'username' acts as the canonical NIC (sub) for multi-identifier linkage
    @Column(name="username", unique = true, nullable = false)
    private String username;

    public String getNic() {
        return this.nic;
    }

    public void setNic(String nic) {
        this.nic = nic;
    }

    @Column(name="email")
    private String email;

    @Column(name = "external_id", unique = true)
    private String externalId;

    @Column(name = "picture")
    private String picture;

    @Column(name = "signature")
    private String signature;

    @Column(name = "nic", length = 13)
    private String nic;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "email_verified")
    private Boolean emailVerified = Boolean.FALSE;

    @NotNull(message = "status is mandatory")
    @Enumerated(EnumType.STRING)
    @Column(name="status", nullable = false)
    private Status status = Status.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    private RoleEntity activeRole;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserRoleAssignment> userRoleAssignments = new ArrayList<>();

    public List<RoleEntity> getRoles() {
        return userRoleAssignments.stream()
                .filter(ura -> ura.getExpiresAt() == null || ura.getExpiresAt().isAfter(java.time.LocalDateTime.now()))
                .map(UserRoleAssignment::getRole)
                .toList();
    }

    @ElementCollection
    @CollectionTable(name = "t_user_custom_fields", joinColumns = @JoinColumn(name = "user_id"))
    @MapKeyColumn(name = "field_key")
    @Column(name = "field_value")
    private Map<String, String> customFields = new HashMap<>();

    /**
     * Free-form metadata exposed through OAuth user management APIs and
     * enriched into issued JWTs by the authorization server.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata = new LinkedHashMap<>();

    // Implementação da interface UserIdentity

    public Integer getInternalId() {
        return this.id;
    }

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
        return this.id != null ? this.id.toString() : null;
    }

    @Override
    public boolean isEmailVerified() {
        return Boolean.TRUE.equals(this.emailVerified);
    }

}