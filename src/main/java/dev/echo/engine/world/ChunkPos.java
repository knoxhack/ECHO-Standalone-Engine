package dev.echo.engine.world;

public record ChunkPos(int x,int z) implements Comparable<ChunkPos>{
    public static ChunkPos fromBlock(int x,int z){return new ChunkPos(Math.floorDiv(x,Chunk.SIZE),Math.floorDiv(z,Chunk.SIZE));}
    public int minBlockX(){return x*Chunk.SIZE;}public int minBlockZ(){return z*Chunk.SIZE;}
    public long distanceSquared(double blockX,double blockZ){double cx=minBlockX()+Chunk.SIZE*0.5;double cz=minBlockZ()+Chunk.SIZE*0.5;double dx=cx-blockX,dz=cz-blockZ;return(long)(dx*dx+dz*dz);}
    @Override public int compareTo(ChunkPos o){int c=Integer.compare(x,o.x);return c!=0?c:Integer.compare(z,o.z);}
}
