package dev.echo.engine.render;

import dev.echo.engine.api.BlockDefinition;
import dev.echo.engine.runtime.registry.RuntimeRegistry;
import dev.echo.engine.world.Chunk;
import dev.echo.engine.world.ChunkPos;
import dev.echo.engine.world.ChunkSnapshot;
import java.util.ArrayList;
import java.util.Map;

public final class ChunkMesher {
    public ChunkMesh build(ChunkSnapshot center,Map<ChunkPos,ChunkSnapshot> snapshots,RuntimeRegistry<BlockDefinition> blocks){ArrayList<MeshFace> faces=new ArrayList<>();int baseX=center.pos().minBlockX(),baseZ=center.pos().minBlockZ();for(int y=0;y<Chunk.HEIGHT;y++)for(int z=0;z<Chunk.SIZE;z++)for(int x=0;x<Chunk.SIZE;x++){int id=center.get(x,y,z);BlockDefinition block=blocks.byRuntimeId(id);if(block.air())continue;int wx=baseX+x,wz=baseZ+z;if(!opaque(snapshots,blocks,wx,y+1,wz))faces.add(face(wx,y,wz,0,1,0,block.argb()));if(!opaque(snapshots,blocks,wx,y-1,wz))faces.add(face(wx,y,wz,0,-1,0,shade(block.argb(),0.55)));if(!opaque(snapshots,blocks,wx+1,y,wz))faces.add(face(wx,y,wz,1,0,0,shade(block.argb(),0.84)));if(!opaque(snapshots,blocks,wx-1,y,wz))faces.add(face(wx,y,wz,-1,0,0,shade(block.argb(),0.72)));if(!opaque(snapshots,blocks,wx,y,wz+1))faces.add(face(wx,y,wz,0,0,1,shade(block.argb(),0.90)));if(!opaque(snapshots,blocks,wx,y,wz-1))faces.add(face(wx,y,wz,0,0,-1,shade(block.argb(),0.66)));}return new ChunkMesh(center.pos(),center.version(),faces);}
    private static boolean opaque(Map<ChunkPos,ChunkSnapshot> snapshots,RuntimeRegistry<BlockDefinition> blocks,int wx,int y,int wz){if(y<0||y>=Chunk.HEIGHT)return false;ChunkPos pos=ChunkPos.fromBlock(wx,wz);ChunkSnapshot snapshot=snapshots.get(pos);if(snapshot==null)return false;return blocks.byRuntimeId(snapshot.get(Math.floorMod(wx,Chunk.SIZE),y,Math.floorMod(wz,Chunk.SIZE))).opaque();}
    private static MeshFace face(int x,int y,int z,int nx,int ny,int nz,int color){float x0=x,y0=y,z0=z,x1=x+1,y1=y+1,z1=z+1;float[] v;if(ny>0)v=new float[]{x0,y1,z0,x0,y1,z1,x1,y1,z1,x1,y1,z0};else if(ny<0)v=new float[]{x0,y0,z1,x0,y0,z0,x1,y0,z0,x1,y0,z1};else if(nx>0)v=new float[]{x1,y0,z1,x1,y0,z0,x1,y1,z0,x1,y1,z1};else if(nx<0)v=new float[]{x0,y0,z0,x0,y0,z1,x0,y1,z1,x0,y1,z0};else if(nz>0)v=new float[]{x0,y0,z1,x1,y0,z1,x1,y1,z1,x0,y1,z1};else v=new float[]{x1,y0,z0,x0,y0,z0,x0,y1,z0,x1,y1,z0};return new MeshFace(v,nx,ny,nz,color);}
    private static int shade(int argb,double factor){int a=(argb>>>24)&255,r=(argb>>>16)&255,g=(argb>>>8)&255,b=argb&255;r=(int)Math.max(0,Math.min(255,r*factor));g=(int)Math.max(0,Math.min(255,g*factor));b=(int)Math.max(0,Math.min(255,b*factor));return(a<<24)|(r<<16)|(g<<8)|b;}
}
