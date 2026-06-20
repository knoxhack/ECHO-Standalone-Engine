package dev.echo.engine.api.graph;

import java.util.Map;

/** Immutable module-facing view of a canonical ECHO Content Graph edge. */
public record GraphEdgeView(
        String id,
        String type,
        String source,
        String target,
        Map<String, Object> attributes
) {
    public GraphEdgeView {
        id = id == null ? "" : id.trim();
        type = required(type, "type");
        source = required(source, "source");
        target = required(target, "target");
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
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

    public String string(String key, String fallback) {
        Object value = attributes.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static String required(String value, String name) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return normalized;
    }
}
