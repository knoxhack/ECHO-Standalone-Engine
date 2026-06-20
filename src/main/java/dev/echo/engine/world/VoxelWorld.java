package dev.echo.engine.world;

import dev.echo.engine.api.BlockDefinition;
import dev.echo.engine.api.EventBus;
import dev.echo.engine.api.ResourceId;
import dev.echo.engine.api.event.BlockChangedEvent;
import dev.echo.engine.runtime.registry.RuntimeRegistry;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class VoxelWorld {
    private final long seed;private final RuntimeRegistry<BlockDefinition> blocks;private final EventBus events;private final ConcurrentHashMap<ChunkPos,Chunk> chunks=new ConcurrentHashMap<>();private final int airId;
    public VoxelWorld(long seed,RuntimeRegistry<BlockDefinition> blocks,EventBus events){this.seed=seed;this.blocks=blocks;this.events=events;this.airId=blocks.runtimeId(ResourceId.parse("echo:air"));}
    public long seed(){return seed;}public RuntimeRegistry<BlockDefinition> blocks(){return blocks;}public Collection<Chunk> chunks(){return List.copyOf(chunks.values());}public Map<ChunkPos,Chunk> chunkMap(){return Map.copyOf(chunks);}public Chunk chunk(ChunkPos pos){return chunks.get(pos);}public boolean hasChunk(ChunkPos pos){return chunks.containsKey(pos);}public Chunk putChunkIfAbsent(Chunk chunk){Chunk previous=chunks.putIfAbsent(chunk.pos(),chunk);return previous==null?chunk:previous;}public Chunk removeChunk(ChunkPos pos){return chunks.remove(pos);}
    public int runtimeIdAt(int x,int y,int z){if(y<0||y>=Chunk.HEIGHT)return airId;Chunk chunk=chunks.get(ChunkPos.fromBlock(x,z));return chunk==null?airId:chunk.get(Math.floorMod(x,Chunk.SIZE),y,Math.floorMod(z,Chunk.SIZE));}
    public BlockDefinition blockAt(int x,int y,int z){return blocks.byRuntimeId(runtimeIdAt(x,y,z));}
    public ResourceId blockIdAt(int x,int y,int z){return blockAt(x,y,z).id();}
    public boolean setBlock(int x,int y,int z,ResourceId id){if(y<0||y>=Chunk.HEIGHT)return false;Chunk chunk=chunks.get(ChunkPos.fromBlock(x,z));if(chunk==null)return false;ResourceId previous=blockIdAt(x,y,z);boolean changed=chunk.set(Math.floorMod(x,Chunk.SIZE),y,Math.floorMod(z,Chunk.SIZE),blocks.runtimeId(id));if(changed&&events!=null)events.publish(new BlockChangedEvent(x,y,z,previous,id));return changed;}
    public boolean solidAt(int x,int y,int z){return blockAt(x,y,z).solid();}
    public boolean opaqueAt(int x,int y,int z){return blockAt(x,y,z).opaque();}
    public int surfaceY(int x,int z){for(int y=Chunk.HEIGHT-1;y>=0;y--)if(solidAt(x,y,z))return y;return 0;}
    public boolean collides(double minX,double minY,double minZ,double maxX,double maxY,double maxZ){int x0=(int)Math.floor(minX),x1=(int)Math.floor(maxX-1e-6),y0=(int)Math.floor(minY),y1=(int)Math.floor(maxY-1e-6),z0=(int)Math.floor(minZ),z1=(int)Math.floor(maxZ-1e-6);for(int y=y0;y<=y1;y++)for(int z=z0;z<=z1;z++)for(int x=x0;x<=x1;x++)if(solidAt(x,y,z))return true;return false;}
    public boolean isBlockNear(ResourceId id,double x,double y,double z,int radius){int cx=(int)Math.floor(x),cy=(int)Math.floor(y),cz=(int)Math.floor(z);for(int by=Math.max(0,cy-radius);by<=Math.min(Chunk.HEIGHT-1,cy+radius);by++)for(int bz=cz-radius;bz<=cz+radius;bz++)for(int bx=cx-radius;bx<=cx+radius;bx++)if(blockIdAt(bx,by,bz).equals(id))return true;return false;}
}
