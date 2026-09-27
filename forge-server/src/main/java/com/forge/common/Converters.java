package com.forge.common;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** JPA JSON 字段转换器：Map/List <-> TEXT 列 */
public class Converters {

    @Converter
    public static class MapConverter implements AttributeConverter<Map<String, Object>, String> {
        @Override public String convertToDatabaseColumn(Map<String, Object> attribute) {
            return attribute == null ? "{}" : JsonUtils.toJson(attribute);
        }
        @Override public Map<String, Object> convertToEntityAttribute(String dbData) {
            return JsonUtils.toMap(dbData);
        }
    }

    @Converter
    public static class StrMapConverter implements AttributeConverter<Map<String, String>, String> {
        @Override public String convertToDatabaseColumn(Map<String, String> attribute) {
            return attribute == null ? "{}" : JsonUtils.toJson(attribute);
        }
        @Override public Map<String, String> convertToEntityAttribute(String dbData) {
            return JsonUtils.toStrMap(dbData);
        }
    }

    @Converter
    public static class StrListConverter implements AttributeConverter<List<String>, String> {
        @Override public String convertToDatabaseColumn(List<String> attribute) {
            return attribute == null ? "[]" : JsonUtils.toJson(attribute);
        }
        @Override public List<String> convertToEntityAttribute(String dbData) {
            if (dbData == null || dbData.isBlank()) return new ArrayList<>();
            try {
                return JsonUtils.mapper().readValue(dbData, JsonUtils.mapper().getTypeFactory()
                        .constructCollectionType(List.class, String.class));
            } catch (Exception e) { return new ArrayList<>(); }
        }
    }
}
