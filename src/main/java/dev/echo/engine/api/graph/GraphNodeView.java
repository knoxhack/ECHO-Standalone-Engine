package dev.echo.engine.api.graph;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable module-facing view of one canonical ECHO Content Graph node. */
public record GraphNodeView(
        String id,
        String moduleId,
        String kind,
        String displayName,
        Map<String, Object> attributes
) {
    public GraphNodeView {
        id = normalizeRequired(id, "id");
        moduleId = normalizeRequired(moduleId, "moduleId");
        kind = normalizeRequired(kind, "kind").toUpperCase(java.util.Locale.ROOT);
        displayName = displayName == null || displayName.isBlank() ? id : displayName.trim();
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public String string(String key, String fallback) {
        Object value = attributes.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    public String requireString(String key) {
        String value = string(key, "").trim();
        if (value.isBlank()) throw new IllegalArgumentException(id + " requires graph attribute " + key);
        return value;
    }

    public boolean bool(String key, boolean fallback) {
        Object value = attributes.get(key);
        if (value instanceof Boolean bool) return bool;
        if (value instanceof String text) return Boolean.parseBoolean(text);
        return fallback;
    }

    public int integer(String key, int fallback) {
        Object value = attributes.get(key);
        if (value instanceof Number number) return number.intValue();
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    public int requireInteger(String key) {
        if (!attributes.containsKey(key)) throw new IllegalArgumentException(id + " requires graph attribute " + key);
        int sentinel = Integer.MIN_VALUE;
        int value = integer(key, sentinel);
        if (value == sentinel) throw new IllegalArgumentException(id + " graph attribute " + key + " is not an integer");
        return value;
    }

    public double decimal(String key, double fallback) {
        Object value = attributes.get(key);
        if (value instanceof Number number) return number.doubleValue();
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    public double requireDecimal(String key) {
        if (!attributes.containsKey(key)) throw new IllegalArgumentException(id + " requires graph attribute " + key);
        double value = decimal(key, Double.NaN);
        if (!Double.isFinite(value)) throw new IllegalArgumentException(id + " graph attribute " + key + " is not numeric");
        return value;
    }

    public List<String> strings(String key) {
        Object value = attributes.get(key);
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(java.util.Objects::nonNull).map(String::valueOf).toList();
    }

    public Map<String, Object> object(String key) {
        Object value = attributes.get(key);
        if (!(value instanceof Map<?, ?> map)) return Map.of();
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        map.forEach((itemKey, itemValue) -> result.put(String.valueOf(itemKey), itemValue));
        return Map.copyOf(result);
    }

    private static String normalizeRequired(String value, String name) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return normalized;
    }
}
