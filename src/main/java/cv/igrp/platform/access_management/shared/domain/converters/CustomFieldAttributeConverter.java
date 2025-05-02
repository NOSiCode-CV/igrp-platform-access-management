package cv.igrp.platform.access_management.shared.domain.converters;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.HashMap;
import java.util.Map;

@Converter
public class CustomFieldAttributeConverter implements AttributeConverter<Map<String, ?>, String> {

    @Override
    public String convertToDatabaseColumn(Map<String, ?> attribute) {
        final ObjectMapper objectMapper = new ObjectMapper();
        if (attribute == null || attribute.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not convert Map to JSON string.", e);
        }
    }

    @Override
    public Map<String, ?> convertToEntityAttribute(String dbData) {
        final ObjectMapper objectMapper = new ObjectMapper();
        if (dbData == null || dbData.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(dbData, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not convert JSON string to Map.", e);
        }
    }

}
