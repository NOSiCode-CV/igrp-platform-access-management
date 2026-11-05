package cv.igrp.platform.access_management.shared.domain.converters;

import tools.jackson.core.type.TypeReference;
import cv.igrp.platform.access_management.shared.infrastructure.json.PersistedJson;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.HashMap;
import java.util.Map;

@Converter
public class CustomFieldAttributeConverter implements AttributeConverter<Map<String, ?>, String> {

    @Override
    public String convertToDatabaseColumn(Map<String, ?> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "{}";
        }
        try {
            return PersistedJson.MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not convert Map to JSON string.", e);
        }
    }

    @Override
    public Map<String, ?> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new HashMap<>();
        }
        try {
            return PersistedJson.MAPPER.readValue(dbData, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not convert JSON string to Map.", e);
        }
    }

}
