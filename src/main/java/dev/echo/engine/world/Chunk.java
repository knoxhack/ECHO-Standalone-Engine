package dev.echo.engine.world;

import java.util.Arrays;

public final class Chunk {
    public static final int SIZE=16;public static final int HEIGHT=64;private final ChunkPos pos;private final int[] blocks=new int[SIZE*HEIGHT*SIZE];private long version;private boolean dirty;
    public Chunk(ChunkPos pos){this.pos=pos;}
    public ChunkPos pos(){return pos;}
    public synchronized int get(int x,int y,int z){if(x<0||x>=SIZE||z<0||z>=SIZE||y<0||y>=HEIGHT)return 0;return blocks[index(x,y,z)];}
    public synchronized boolean set(int x,int y,int z,int value){if(x<0||x>=SIZE||z<0||z>=SIZE||y<0||y>=HEIGHT)return false;int index=index(x,y,z);if(blocks[index]==value)return false;blocks[index]=value;version++;dirty=true;return true;}
    public synchronized long version(){return version;}
    public synchronized boolean dirty(){return dirty;}
    public synchronized void markClean(){dirty=false;}
    public synchronized ChunkSnapshot snapshot(){return new ChunkSnapshot(pos,version,Arrays.copyOf(blocks,blocks.length));}
    public synchronized int[] copyBlocks(){return Arrays.copyOf(blocks,blocks.length);}
    public synchronized void replaceBlocks(int[] source,long restoredVersion){if(source.length!=blocks.length)throw new IllegalArgumentException("Invalid chunk block array");System.arraycopy(source,0,blocks,0,blocks.length);version=Math.max(0,restoredVersion);dirty=false;}
    private static int index(int x,int y,int z){return(y*SIZE+z)*SIZE+x;}
}
