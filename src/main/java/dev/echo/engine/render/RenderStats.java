package dev.echo.engine.render;

public record RenderStats(int chunks,int sourceFaces,int drawnFaces,int culledFaces,long renderNanos){
    public static final RenderStats EMPTY=new RenderStats(0,0,0,0,0);
}
