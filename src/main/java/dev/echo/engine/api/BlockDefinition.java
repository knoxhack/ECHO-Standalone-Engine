package dev.echo.engine.api;

import java.util.Map;

public record BlockDefinition(
        ResourceId id,
        String displayName,
        int argb,
        boolean solid,
        boolean opaque,
        double hardness,
        double hazardPerSecond,
        int emittedLight,
        Map<String, String> properties
) {
    public BlockDefinition {
        if (id == null) throw new IllegalArgumentException("block id is required");
        displayName = displayName == null || displayName.isBlank() ? id.toString() : displayName.trim();
        hardness = Math.max(0.0, hardness);
        hazardPerSecond = Math.max(0.0, hazardPerSecond);
        emittedLight = Math.max(0, Math.min(15, emittedLight));
        properties = properties == null ? Map.of() : Map.copyOf(properties);
    }
    public boolean air() { return id.equals(ResourceId.parse("echo:air")); }
}
