package dev.echo.engine.game;

import dev.echo.engine.api.EntityDefinition;
import dev.echo.engine.api.ResourceId;
import dev.echo.engine.render.ChunkMesh;
import dev.echo.engine.render.MeshFace;
import dev.echo.engine.runtime.registry.RuntimeRegistry;
import dev.echo.engine.world.ChunkPos;
import dev.echo.engine.world.VoxelWorld;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public final class EntitySystem {
    private final VoxelWorld world;private final RuntimeRegistry<EntityDefinition> definitions;private final ArrayList<WorldEntity> entities=new ArrayList<>();private final Random random;private double spawnClock;private long version;
    public EntitySystem(VoxelWorld world,RuntimeRegistry<EntityDefinition> definitions){this.world=world;this.definitions=definitions;this.random=new Random(world.seed()^0x6E74697479L);}
    public void tick(Player player,double dt){spawnClock+=dt;if(spawnClock>=2.5){spawnClock-=2.5;spawn(player);}for(WorldEntity entity:new ArrayList<>(entities)){entity.tickCooldown(dt);double dx=player.x()-entity.x(),dz=player.z()-entity.z(),distance=Math.hypot(dx,dz);if(entity.definition().hostile()&&distance<28&&distance>0.01){double speed=entity.definition().moveSpeed()*dt;double nx=dx/distance,nz=dz/distance;move(entity,nx*speed,nz*speed);}if(entity.definition().hostile()&&distance<1.35&&Math.abs(player.y()-entity.y())<2&&entity.attackCooldown()<=0){player.setHealth(player.health()-2.0);entity.resetAttackCooldown();}if(distance>72||!entity.alive())entities.remove(entity);}version++;}
    private void move(WorldEntity entity,double dx,double dz){double nextX=entity.x()+dx,nextZ=entity.z()+dz;int surface=world.surfaceY((int)Math.floor(nextX),(int)Math.floor(nextZ));double nextY=surface+1.01;if(!world.collides(nextX-0.3,nextY,nextZ-0.3,nextX+0.3,nextY+1.4,nextZ+0.3))entity.setPosition(nextX,nextY,nextZ);}
    private void spawn(Player player){if(entities.size()>=18||definitions.values().isEmpty())return;List<EntityDefinition> eligible=definitions.values().stream().filter(e->e.spawnWeight()>0).toList();if(eligible.isEmpty())return;int total=eligible.stream().mapToInt(EntityDefinition::spawnWeight).sum(),roll=random.nextInt(Math.max(1,total));EntityDefinition selected=eligible.get(0);for(EntityDefinition definition:eligible){roll-=definition.spawnWeight();if(roll<0){selected=definition;break;}}double angle=random.nextDouble()*Math.PI*2,distance=12+random.nextDouble()*18;double x=player.x()+Math.cos(angle)*distance,z=player.z()+Math.sin(angle)*distance;int y=world.surfaceY((int)Math.floor(x),(int)Math.floor(z))+1;if(y<=1)return;entities.add(new WorldEntity(selected,x,y,z));}
    public String attackFrom(Player player,double maxDistance){double yaw=Math.toRadians(player.yaw()),pitch=Math.toRadians(player.pitch());double dx=Math.sin(yaw)*Math.cos(pitch),dy=Math.sin(pitch),dz=Math.cos(yaw)*Math.cos(pitch);WorldEntity best=null;double bestT=maxDistance;for(WorldEntity entity:entities){double cx=entity.x(),cy=entity.y()+0.7,cz=entity.z();double rx=cx-player.x(),ry=cy-player.eyeY(),rz=cz-player.z();double t=rx*dx+ry*dy+rz*dz;if(t<0||t>bestT)continue;double qx=player.x()+dx*t,qy=player.eyeY()+dy*t,qz=player.z()+dz*t;double distSq=(cx-qx)*(cx-qx)+(cy-qy)*(cy-qy)+(cz-qz)*(cz-qz);if(distSq<0.55*0.55){best=entity;bestT=t;}}if(best==null)return"";best.damage(4);String message="Hit "+best.definition().displayName()+" ("+(int)Math.ceil(best.health())+" HP)";if(!best.alive()){String drop=best.definition().properties().get("drop");if(drop!=null&&!drop.isBlank())player.inventory().add(ResourceId.parse(drop),1);message=best.definition().displayName()+" defeated";}return message;}
    public List<WorldEntity> entities(){return List.copyOf(entities);}public ChunkMesh renderMesh(){ArrayList<MeshFace> faces=new ArrayList<>();for(WorldEntity entity:entities)addCube(faces,entity.x()-0.32,entity.y(),entity.z()-0.32,0.64,1.35,entity.definition().argb());return new ChunkMesh(new ChunkPos(0,0),version,faces);}
    private static void addCube(List<MeshFace> out,double x,double y,double z,double w,double h,int color){float x0=(float)x,x1=(float)(x+w),y0=(float)y,y1=(float)(y+h),z0=(float)z,z1=(float)(z+w);out.add(new MeshFace(new float[]{x0,y1,z0,x0,y1,z1,x1,y1,z1,x1,y1,z0},0,1,0,color));out.add(new MeshFace(new float[]{x0,y0,z1,x0,y0,z0,x1,y0,z0,x1,y0,z1},0,-1,0,shade(color,.55)));out.add(new MeshFace(new float[]{x1,y0,z1,x1,y0,z0,x1,y1,z0,x1,y1,z1},1,0,0,shade(color,.84)));out.add(new MeshFace(new float[]{x0,y0,z0,x0,y0,z1,x0,y1,z1,x0,y1,z0},-1,0,0,shade(color,.72)));out.add(new MeshFace(new float[]{x0,y0,z1,x1,y0,z1,x1,y1,z1,x0,y1,z1},0,0,1,shade(color,.9)));out.add(new MeshFace(new float[]{x1,y0,z0,x0,y0,z0,x0,y1,z0,x1,y1,z0},0,0,-1,shade(color,.66)));}
    private static int shade(int argb,double f){int a=(argb>>>24)&255,r=(int)(((argb>>>16)&255)*f),g=(int)(((argb>>>8)&255)*f),b=(int)((argb&255)*f);return(a<<24)|(r<<16)|(g<<8)|b;}
}
