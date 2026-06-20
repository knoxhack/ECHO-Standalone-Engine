package dev.echo.engine.world;

public record ChunkSnapshot(ChunkPos pos,long version,int[] blocks){
    public int get(int x,int y,int z){if(x<0||x>=Chunk.SIZE||z<0||z>=Chunk.SIZE||y<0||y>=Chunk.HEIGHT)return 0;return blocks[(y*Chunk.SIZE+z)*Chunk.SIZE+x];}
}
