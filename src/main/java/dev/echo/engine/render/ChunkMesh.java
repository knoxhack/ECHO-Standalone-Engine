package dev.echo.engine.render;

import dev.echo.engine.world.ChunkPos;
import java.util.List;

public record ChunkMesh(ChunkPos pos,long sourceVersion,List<MeshFace> faces){
    public ChunkMesh { faces=faces==null?List.of():List.copyOf(faces); }
}
