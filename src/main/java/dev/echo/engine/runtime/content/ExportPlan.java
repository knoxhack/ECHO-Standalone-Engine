package dev.echo.engine.runtime.content;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public record ExportPlan(
        String schemaVersion,
        String moduleId,
        String runtimeTarget,
        Map<String, ExportBinding> bindings
) {
    public ExportPlan {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank()
                ? "echo.content_graph.export_plan.v1"
                : schemaVersion.trim();
        moduleId = required(moduleId, "moduleId");
        runtimeTarget = required(runtimeTarget, "runtimeTarget");
        bindings = bindings == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(bindings));
    }

    public Optional<ExportBinding> binding(String nodeId) {
        return Optional.ofNullable(bindings.get(nodeId));
    }

    private static String required(String value, String name) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return normalized;
    }
}
