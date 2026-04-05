package cv.igrp.platform.access_management.shared.infrastructure.persistence.entity;

import cv.igrp.framework.stereotype.IgrpEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@IgrpEntity
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "t_user_identifier", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"type", "value_normalized"})
})
public class UserIdentifierEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private IGRPUserEntity user;

    @Column(name = "type", nullable = false)
    private String type; // EMAIL or PHONE

    @Column(name = "value_normalized", nullable = false)
    private String valueNormalized;

    @Column(name = "verified", nullable = false)
    private Boolean verified = false;
}
