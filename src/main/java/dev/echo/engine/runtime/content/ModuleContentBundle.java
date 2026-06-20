package dev.echo.engine.runtime.content;

import dev.echo.engine.api.graph.GraphEdgeView;
import dev.echo.engine.api.graph.GraphNodeView;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record ModuleContentBundle(
        String schemaVersion,
        String moduleId,
        Path modulePath,
        List<GraphNodeView> nodes,
        List<GraphEdgeView> edges,
        List<ContentFeature> features,
        Map<String, ExportPlan> exportPlans,
        Map<String, Object> provenance,
        List<String> unresolvedReferences
) {
    public ModuleContentBundle {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank()
                ? "echo.content_graph.v1"
                : schemaVersion.trim();
        moduleId = moduleId == null ? "" : moduleId.trim();
        if (moduleId.isBlank()) throw new IllegalArgumentException("moduleId must not be blank");
        modulePath = modulePath == null ? Path.of(".") : modulePath.toAbsolutePath().normalize();
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
        features = features == null ? List.of() : List.copyOf(features);
        exportPlans = exportPlans == null ? Map.of() : Map.copyOf(exportPlans);
        provenance = provenance == null ? Map.of() : Map.copyOf(provenance);
        unresolvedReferences = unresolvedReferences == null ? List.of() : List.copyOf(unresolvedReferences);
    }

    public ExportPlan requirePlan(String runtimeTarget) {
        ExportPlan plan = exportPlans.get(runtimeTarget);
        if (plan == null) throw new IllegalArgumentException(
                "Module " + moduleId + " has no export plan for " + runtimeTarget
        );
        return plan;
    }
}
