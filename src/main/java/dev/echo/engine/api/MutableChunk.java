package dev.echo.engine.api;

public interface MutableChunk {
    int size();
    int height();
    int chunkX();
    int chunkZ();
    ResourceId block(int localX, int y, int localZ);
    void setBlock(int localX, int y, int localZ, ResourceId blockId);
}
