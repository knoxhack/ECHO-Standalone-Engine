package dev.echo.engine.world;

import dev.echo.engine.api.WorldGeneratorDefinition;
import dev.echo.engine.runtime.registry.DefaultContentRegistries;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public final class WorldStreamer implements AutoCloseable {
    private final VoxelWorld world;private final DefaultContentRegistries registries;private final List<WorldGeneratorDefinition> generators;private final ExecutorService workers;private final ConcurrentHashMap<ChunkPos,CompletableFuture<Chunk>> pending=new ConcurrentHashMap<>();private int maxCachedChunks=256;
    public WorldStreamer(VoxelWorld world,DefaultContentRegistries registries,int workerCount){this.world=world;this.registries=registries;this.generators=registries.worldGenerators().values().stream().sorted(Comparator.comparingInt(WorldGeneratorDefinition::priority)).toList();ThreadFactory tf=r->{Thread t=new Thread(r,"echo-worldgen");t.setDaemon(true);return t;};workers=Executors.newFixedThreadPool(Math.max(1,workerCount),tf);}
    public void update(double playerX,double playerZ,int radius){ChunkPos center=ChunkPos.fromBlock((int)Math.floor(playerX),(int)Math.floor(playerZ));ArrayList<ChunkPos> required=new ArrayList<>();for(int dz=-radius;dz<=radius;dz++)for(int dx=-radius;dx<=radius;dx++)required.add(new ChunkPos(center.x()+dx,center.z()+dz));required.sort(Comparator.comparingLong(pos->pos.distanceSquared(playerX,playerZ)));for(ChunkPos pos:required)request(pos);evict(center,radius+3);}
    public CompletableFuture<Chunk> request(ChunkPos pos){Chunk existing=world.chunk(pos);if(existing!=null)return CompletableFuture.completedFuture(existing);return pending.computeIfAbsent(pos,key->CompletableFuture.supplyAsync(()->generate(key),workers).whenComplete((chunk,error)->{pending.remove(key);if(error==null)world.putChunkIfAbsent(chunk);else error.printStackTrace(System.err);}));}
    private Chunk generate(ChunkPos pos){Chunk chunk=new Chunk(pos);MutableChunkAdapter adapter=new MutableChunkAdapter(chunk,registries.runtimeBlocks());for(WorldGeneratorDefinition generator:generators)generator.generator().generate(adapter,world.seed(),pos.x(),pos.z());return chunk;}
    private void evict(ChunkPos center,int keepRadius){if(world.chunks().size()<=maxCachedChunks)return;Set<ChunkPos> pendingKeys=Set.copyOf(pending.keySet());world.chunks().stream().filter(c->!c.dirty()).filter(c->!pendingKeys.contains(c.pos())).filter(c->Math.abs(c.pos().x()-center.x())>keepRadius||Math.abs(c.pos().z()-center.z())>keepRadius).sorted(Comparator.comparingLong((Chunk c)->-c.pos().distanceSquared(center.minBlockX(),center.minBlockZ()))).limit(Math.max(0,world.chunks().size()-maxCachedChunks)).forEach(c->world.removeChunk(c.pos()));}
    public int pendingCount(){return pending.size();}public void setMaxCachedChunks(int value){maxCachedChunks=Math.max(64,value);}
    @Override public void close(){workers.shutdownNow();pending.clear();}
}
