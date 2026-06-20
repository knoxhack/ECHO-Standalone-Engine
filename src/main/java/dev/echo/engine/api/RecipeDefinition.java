package dev.echo.engine.api;

import java.util.Map;

public record RecipeDefinition(
        ResourceId id,
        Map<ResourceId, Integer> ingredients,
        ResourceId result,
        int resultCount
) {
    public RecipeDefinition {
        if (id == null || result == null) throw new IllegalArgumentException("recipe id and result are required");
        ingredients = ingredients == null ? Map.of() : Map.copyOf(ingredients);
        resultCount = Math.max(1, resultCount);
    }
}
