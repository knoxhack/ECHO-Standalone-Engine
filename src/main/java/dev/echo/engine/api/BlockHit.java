package dev.echo.engine.api;

public record BlockHit(
        int x,
        int y,
        int z,
        int adjacentX,
        int adjacentY,
        int adjacentZ,
        ResourceId blockId,
        double distance
) { }
