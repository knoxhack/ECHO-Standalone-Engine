package dev.echo.engine.world;

import dev.echo.engine.api.BlockDefinition;
import dev.echo.engine.api.ResourceId;
import dev.echo.engine.runtime.module.DefaultEventBus;
import dev.echo.engine.runtime.registry.DefaultContentRegistries;
import dev.echo.engine.test.TestSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class ChunkPaletteSaveTest {
    private ChunkPaletteSaveTest(){}
    public static void run()throws Exception{Path root=Files.createTempDirectory("echo-palette-test");ResourceId block=ResourceId.parse("test:marker");DefaultContentRegistries first=new DefaultContentRegistries();first.blocks().register(block,new BlockDefinition(block,"Marker",0xFF123456,true,true,1,0,0,Map.of()));first.freeze();VoxelWorld world=new VoxelWorld(7,first.runtimeBlocks(),new DefaultEventBus());Chunk chunk=new Chunk(new ChunkPos(0,0));world.putChunkIfAbsent(chunk);world.setBlock(3,4,5,block);new WorldStorage().saveAll(root,world);
        DefaultContentRegistries second=new DefaultContentRegistries();ResourceId earlier=ResourceId.parse("test:earlier");second.blocks().register(earlier,new BlockDefinition(earlier,"Earlier",0xFF654321,true,true,1,0,0,Map.of()));second.blocks().register(block,new BlockDefinition(block,"Marker",0xFF123456,true,true,1,0,0,Map.of()));second.freeze();VoxelWorld restored=new VoxelWorld(7,second.runtimeBlocks(),new DefaultEventBus());new WorldStorage().loadAll(root,restored);TestSupport.require(restored.blockIdAt(3,4,5).equals(block),"chunk palette must survive runtime id reorder");}
}
