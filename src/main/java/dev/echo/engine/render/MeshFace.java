package dev.echo.engine.render;

public record MeshFace(float[] vertices,float normalX,float normalY,float normalZ,int argb){
    public MeshFace { if(vertices==null||vertices.length!=12)throw new IllegalArgumentException("quad requires four xyz vertices"); }
    public double centerX(){return(vertices[0]+vertices[3]+vertices[6]+vertices[9])*0.25;}public double centerY(){return(vertices[1]+vertices[4]+vertices[7]+vertices[10])*0.25;}public double centerZ(){return(vertices[2]+vertices[5]+vertices[8]+vertices[11])*0.25;}
}
