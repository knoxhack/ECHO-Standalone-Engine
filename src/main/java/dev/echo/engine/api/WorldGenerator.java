package dev.echo.engine.api;

@FunctionalInterface
public interface WorldGenerator {
    void generate(MutableChunk chunk, long seed, int chunkX, int chunkZ);
}
