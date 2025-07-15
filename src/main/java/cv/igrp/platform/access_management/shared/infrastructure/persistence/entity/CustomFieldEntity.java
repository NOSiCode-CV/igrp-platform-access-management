package cv.igrp.platform.access_management.shared.infrastructure.persistence.entity;

import cv.igrp.platform.access_management.shared.config.AuditEntity;
import cv.igrp.framework.stereotype.IgrpEntity;
import cv.igrp.platform.access_management.shared.domain.converters.CustomFieldAttributeConverter;
import jakarta.persistence.*;
import lombok.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.ColumnTransformer;

import java.util.Map;


@Getter
@Setter
@ToString
@IgrpEntity
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "t_custom_field",
  uniqueConstraints = {
    @UniqueConstraint(
      name = "custom_field_table_name_record_id_uk",
      columnNames = {
        "table_name","record_id"
      }
    )
  })
public class CustomFieldEntity extends AuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    private Integer id;

  

    @NotBlank(message = "tableName is mandatory")
    @Column(name="table_name", nullable = false)
    private String tableName;

  
    @NotNull(message = "recordId is mandatory")
    @Column(name="record_id", nullable = false)
    private Integer recordId;


    @Column(columnDefinition = "jsonb")
    @Convert(converter = CustomFieldAttributeConverter.class)
    @ColumnTransformer(write = "?::jsonb")
    private Map<String, Object> fields;
  
}
