package cv.igrp.platform.access_management.shared.domain.models;

import cv.igrp.platform.access_management.shared.config.AuditEntity;
import cv.igrp.framework.stereotype.IgrpEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

@Audited
@Getter
@Setter
@ToString
@IgrpEntity
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "t_igrp_user")
public class IGRPUser extends AuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", unique = true, nullable = false)
    private Integer id;

    @NotBlank(message = "name is mandatory")
    @Column(name="name", nullable = false)
    private String name;

    @Column(name="username", unique = true)
    private String username;

    @Column(name="email", unique = true)
    private String email;

    // Relacionamento ManyToMany com Role (papéis)
    @ManyToMany
    @JoinTable(
        name = "user_roles", // Nome da tabela de junção
        joinColumns = @JoinColumn(name = "user_id"), // Coluna que se refere ao usuário
        inverseJoinColumns = @JoinColumn(name = "role_id") // Coluna que se refere ao papel (role)
    )
    private List<Role> roles; // Lista de papéis (roles) atribuídos ao usuário

}