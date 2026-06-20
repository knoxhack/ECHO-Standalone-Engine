package dev.echo.engine.api;

public interface WorldAccess {
    ResourceId blockAt(int x, int y, int z);
    boolean setBlock(int x, int y, int z, ResourceId blockId);
    boolean isBlockNear(ResourceId blockId, double x, double y, double z, int radius);
    int surfaceY(int x, int z);
}
