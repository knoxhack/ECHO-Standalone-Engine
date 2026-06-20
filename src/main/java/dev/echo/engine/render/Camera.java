package dev.echo.engine.render;

public record Camera(double x,double y,double z,double yawDegrees,double pitchDegrees,double fovDegrees){
    public double forwardX(){return Math.sin(Math.toRadians(yawDegrees))*Math.cos(Math.toRadians(pitchDegrees));}
    public double forwardY(){return Math.sin(Math.toRadians(pitchDegrees));}
    public double forwardZ(){return Math.cos(Math.toRadians(yawDegrees))*Math.cos(Math.toRadians(pitchDegrees));}
}
