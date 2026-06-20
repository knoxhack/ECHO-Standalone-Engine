package dev.echo.engine.api;

public record WorldGeneratorDefinition(ResourceId id, int priority, WorldGenerator generator) {
    public WorldGeneratorDefinition {
        if (id == null || generator == null) throw new IllegalArgumentException("world generator id and implementation are required");
    }
}
