package dev.echo.engine.render;

import dev.echo.engine.api.BlockDefinition;
import dev.echo.engine.runtime.registry.RuntimeRegistry;
import dev.echo.engine.world.Chunk;
import dev.echo.engine.world.ChunkPos;
import dev.echo.engine.world.ChunkSnapshot;
import dev.echo.engine.world.VoxelWorld;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MeshScheduler implements AutoCloseable {
    private final VoxelWorld world;private final RuntimeRegistry<BlockDefinition> blocks;private final ChunkMesher mesher=new ChunkMesher();private final ExecutorService workers;private final ConcurrentHashMap<ChunkPos,ChunkMesh> meshes=new ConcurrentHashMap<>();private final ConcurrentHashMap<ChunkPos,Long> pending=new ConcurrentHashMap<>();
    public MeshScheduler(VoxelWorld world,RuntimeRegistry<BlockDefinition> blocks,int workerCount){this.world=world;this.blocks=blocks;this.workers=Executors.newFixedThreadPool(Math.max(1,workerCount),r->{Thread t=new Thread(r,"echo-mesher");t.setDaemon(true);return t;});}
    public void syncVisible(double playerX,double playerZ,int radius){ChunkPos center=ChunkPos.fromBlock((int)Math.floor(playerX),(int)Math.floor(playerZ));ArrayList<Chunk> visible=new ArrayList<>();for(Chunk chunk:world.chunks())if(Math.abs(chunk.pos().x()-center.x())<=radius&&Math.abs(chunk.pos().z()-center.z())<=radius)visible.add(chunk);visible.sort(Comparator.comparingLong(c->c.pos().distanceSquared(playerX,playerZ)));for(Chunk chunk:visible){ChunkMesh current=meshes.get(chunk.pos());if(current==null||current.sourceVersion()!=chunk.version())request(chunk);}meshes.keySet().removeIf(pos->Math.abs(pos.x()-center.x())>radius+1||Math.abs(pos.z()-center.z())>radius+1);}
    private void request(Chunk chunk){long version=chunk.version();Long existing=pending.putIfAbsent(chunk.pos(),version);if(existing!=null&&existing==version)return;Map<ChunkPos,ChunkSnapshot> snapshots=snapshotsAround(chunk.pos());ChunkSnapshot center=snapshots.get(chunk.pos());if(center==null){pending.remove(chunk.pos());return;}CompletableFuture.supplyAsync(()->mesher.build(center,snapshots,blocks),workers).whenComplete((mesh,error)->{pending.remove(chunk.pos());if(error!=null){error.printStackTrace(System.err);return;}Chunk live=world.chunk(mesh.pos());if(live!=null&&live.version()==mesh.sourceVersion())meshes.put(mesh.pos(),mesh);});}
    private Map<ChunkPos,ChunkSnapshot> snapshotsAround(ChunkPos center){HashMap<ChunkPos,ChunkSnapshot> out=new HashMap<>();for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++){ChunkPos pos=new ChunkPos(center.x()+dx,center.z()+dz);Chunk chunk=world.chunk(pos);if(chunk!=null)out.put(pos,chunk.snapshot());}return Map.copyOf(out);}
    public Collection<ChunkMesh> meshes(){return List.copyOf(meshes.values());}public int pendingCount(){return pending.size();}public int faceCount(){return meshes.values().stream().mapToInt(m->m.faces().size()).sum();}
    @Override public void close(){workers.shutdownNow();meshes.clear();pending.clear();}
}
