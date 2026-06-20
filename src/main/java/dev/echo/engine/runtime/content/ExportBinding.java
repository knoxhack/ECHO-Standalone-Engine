package dev.echo.engine.runtime.content;

import java.util.Map;

public record ExportBinding(
        String nodeId,
        String runtimeId,
        String adapter,
        String kind,
        Map<String, Object> attributes
) {
    public ExportBinding {
        nodeId = required(nodeId, "nodeId");
        runtimeId = required(runtimeId, "runtimeId");
        adapter = adapter == null ? "" : adapter.trim();
        kind = kind == null ? "" : kind.trim().toUpperCase(java.util.Locale.ROOT);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    private static String required(String value, String name) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return normalized;
    }
}
