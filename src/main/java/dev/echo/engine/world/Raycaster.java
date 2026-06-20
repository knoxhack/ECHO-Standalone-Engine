package dev.echo.engine.world;

import dev.echo.engine.api.BlockHit;
import java.util.Optional;

public final class Raycaster {
    private Raycaster(){}
    public static Optional<BlockHit> cast(VoxelWorld world,double ox,double oy,double oz,double dx,double dy,double dz,double maxDistance){double length=Math.sqrt(dx*dx+dy*dy+dz*dz);if(length<1e-9)return Optional.empty();dx/=length;dy/=length;dz/=length;int previousX=(int)Math.floor(ox),previousY=(int)Math.floor(oy),previousZ=(int)Math.floor(oz);for(double distance=0;distance<=maxDistance;distance+=0.05){int x=(int)Math.floor(ox+dx*distance),y=(int)Math.floor(oy+dy*distance),z=(int)Math.floor(oz+dz*distance);if(world.blockAt(x,y,z).solid()){return Optional.of(new BlockHit(x,y,z,previousX,previousY,previousZ,world.blockIdAt(x,y,z),distance));}previousX=x;previousY=y;previousZ=z;}return Optional.empty();}
}
