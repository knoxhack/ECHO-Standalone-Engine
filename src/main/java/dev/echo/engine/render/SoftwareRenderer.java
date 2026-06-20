package dev.echo.engine.render;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public final class SoftwareRenderer {
    private static final double NEAR=0.08;private static final int MAX_DRAW_FACES=45000;private final ArrayList<ProjectedFace> projected=new ArrayList<>();private RenderStats lastStats=RenderStats.EMPTY;
    public RenderStats render(Graphics2D g,int width,int height,Camera camera,Collection<ChunkMesh> meshes,double viewDistance){long start=System.nanoTime();g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_SPEED);g.setPaint(new GradientPaint(0,0,new Color(68,75,83),0,height,new Color(176,119,84)));g.fillRect(0,0,width,height);projected.clear();int source=0,culled=0;double yaw=Math.toRadians(camera.yawDegrees()),pitch=Math.toRadians(camera.pitchDegrees());double cy=Math.cos(yaw),sy=Math.sin(yaw),cp=Math.cos(pitch),sp=Math.sin(pitch);double focal=(width*0.5)/Math.tan(Math.toRadians(camera.fovDegrees()*0.5));for(ChunkMesh mesh:meshes)for(MeshFace face:mesh.faces()){source++;double cx=face.centerX(),cyc=face.centerY(),cz=face.centerZ();double vx=camera.x()-cx,vy=camera.y()-cyc,vz=camera.z()-cz;if(face.normalX()*vx+face.normalY()*vy+face.normalZ()*vz<=0){culled++;continue;}double distanceSq=vx*vx+vy*vy+vz*vz;if(distanceSq>viewDistance*viewDistance){culled++;continue;}int[] xs=new int[4],ys=new int[4];double depth=0;boolean clipped=false;float[] vertices=face.vertices();for(int i=0;i<4;i++){double dx=vertices[i*3]-camera.x(),dy=vertices[i*3+1]-camera.y(),dz=vertices[i*3+2]-camera.z();double x1=cy*dx-sy*dz;double z1=sy*dx+cy*dz;double y2=cp*dy-sp*z1;double z2=sp*dy+cp*z1;if(z2<=NEAR){clipped=true;break;}xs[i]=(int)Math.round(width*0.5+x1/z2*focal);ys[i]=(int)Math.round(height*0.5-y2/z2*focal);depth+=z2;}if(clipped){culled++;continue;}depth*=0.25;projected.add(new ProjectedFace(xs,ys,depth,fog(face.argb(),depth,viewDistance)));}
        projected.sort(Comparator.comparingDouble(ProjectedFace::depth).reversed());int from=Math.max(0,projected.size()-MAX_DRAW_FACES);int drawn=0;for(int i=from;i<projected.size();i++){ProjectedFace face=projected.get(i);g.setColor(new Color(face.argb(),true));g.fillPolygon(new Polygon(face.xs(),face.ys(),4));drawn++;}lastStats=new RenderStats(meshes.size(),source,drawn,culled,System.nanoTime()-start);return lastStats;}
    private static int fog(int argb,double depth,double maxDistance){double start=maxDistance*0.55;double t=Math.max(0,Math.min(1,(depth-start)/Math.max(1,maxDistance-start)));int fog=0xFF927865;int a=(argb>>>24)&255,r=(argb>>>16)&255,g=(argb>>>8)&255,b=argb&255;int fr=(fog>>>16)&255,fg=(fog>>>8)&255,fb=fog&255;r=(int)(r+(fr-r)*t);g=(int)(g+(fg-g)*t);b=(int)(b+(fb-b)*t);return(a<<24)|(r<<16)|(g<<8)|b;}
    public RenderStats lastStats(){return lastStats;}
    private record ProjectedFace(int[] xs,int[] ys,double depth,int argb){}
}
