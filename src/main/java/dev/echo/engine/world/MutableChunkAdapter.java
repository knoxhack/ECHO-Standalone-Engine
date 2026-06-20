package dev.echo.engine.world;

import dev.echo.engine.api.MutableChunk;
import dev.echo.engine.api.ResourceId;
import dev.echo.engine.runtime.registry.RuntimeRegistry;
import dev.echo.engine.api.BlockDefinition;

final class MutableChunkAdapter implements MutableChunk {
    private final Chunk chunk;private final RuntimeRegistry<BlockDefinition> blocks;
    MutableChunkAdapter(Chunk chunk,RuntimeRegistry<BlockDefinition> blocks){this.chunk=chunk;this.blocks=blocks;}
    @Override public int size(){return Chunk.SIZE;}@Override public int height(){return Chunk.HEIGHT;}@Override public int chunkX(){return chunk.pos().x();}@Override public int chunkZ(){return chunk.pos().z();}
    @Override public ResourceId block(int x,int y,int z){return blocks.byRuntimeId(chunk.get(x,y,z)).id();}
    @Override public void setBlock(int x,int y,int z,ResourceId blockId){chunk.set(x,y,z,blocks.runtimeId(blockId));}
}
