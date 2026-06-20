package dev.echo.engine.runtime.content;

import java.util.List;
import java.util.Map;

public record ContentFeature(
        String id,
        String moduleId,
        String name,
        List<String> nodeIds,
        Map<String, Object> attributes
) {
    public ContentFeature {
        id = id == null ? "" : id.trim();
        moduleId = moduleId == null ? "" : moduleId.trim();
        name = name == null || name.isBlank() ? id : name.trim();
        nodeIds = nodeIds == null ? List.of() : List.copyOf(nodeIds);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
