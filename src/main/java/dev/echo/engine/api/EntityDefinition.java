package dev.echo.engine.api;

import java.util.Map;

public record EntityDefinition(
        ResourceId id,
        String displayName,
        int argb,
        double maxHealth,
        double moveSpeed,
        boolean hostile,
        int spawnWeight,
        Map<String, String> properties
) {
    public EntityDefinition {
        if (id == null) throw new IllegalArgumentException("entity id is required");
        displayName = displayName == null || displayName.isBlank() ? id.toString() : displayName.trim();
        maxHealth = Math.max(1.0, maxHealth);
        moveSpeed = Math.max(0.0, moveSpeed);
        spawnWeight = Math.max(0, spawnWeight);
        properties = properties == null ? Map.of() : Map.copyOf(properties);
    }
}
