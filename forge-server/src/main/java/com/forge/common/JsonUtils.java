package com.forge.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

public final class JsonUtils {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private JsonUtils() {}

    public static ObjectMapper mapper() { return MAPPER; }

    public static String toJson(Object obj) {
        try { return MAPPER.writeValueAsString(obj); }
        catch (Exception e) { throw new IllegalStateException("JSON 序列化失败", e); }
    }

    public static Map<String, Object> toMap(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try { return MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {}); }
        catch (Exception e) { return new LinkedHashMap<>(); }
    }

    public static Map<String, String> toStrMap(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try { return MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, String>>() {}); }
        catch (Exception e) { return new LinkedHashMap<>(); }
    }
}
