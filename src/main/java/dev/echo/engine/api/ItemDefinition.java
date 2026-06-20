package dev.echo.engine.api;

import java.util.Map;

public record ItemDefinition(
        ResourceId id,
        String displayName,
        int maxStack,
        ResourceId placesBlock,
        double foodRestore,
        double hydrationRestore,
        Map<String, String> properties
) {
    public ItemDefinition {
        if (id == null) throw new IllegalArgumentException("item id is required");
        displayName = displayName == null || displayName.isBlank() ? id.toString() : displayName.trim();
        maxStack = Math.max(1, Math.min(999, maxStack));
        foodRestore = Math.max(0.0, foodRestore);
        hydrationRestore = Math.max(0.0, hydrationRestore);
        properties = properties == null ? Map.of() : Map.copyOf(properties);
    }
}
