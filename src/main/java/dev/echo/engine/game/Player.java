package dev.echo.engine.game;

import dev.echo.engine.api.PlayerAccess;
import dev.echo.engine.api.ResourceId;

public final class Player implements PlayerAccess {
    public static final double WIDTH=0.60,HEIGHT=1.80,EYE_HEIGHT=1.62;private double x,y,z,velocityX,velocityY,velocityZ,yaw,pitch,health=20,hunger=20,hydration=20,exposure;private boolean grounded;private final Inventory inventory;
    public Player(Inventory inventory){this.inventory=inventory;}
    public Inventory inventory(){return inventory;}@Override public double x(){return x;}@Override public double y(){return y;}@Override public double z(){return z;}public double velocityX(){return velocityX;}public double velocityY(){return velocityY;}public double velocityZ(){return velocityZ;}public double yaw(){return yaw;}public double pitch(){return pitch;}public boolean grounded(){return grounded;}
    public void setPosition(double x,double y,double z){this.x=x;this.y=y;this.z=z;}public void setVelocity(double x,double y,double z){velocityX=x;velocityY=y;velocityZ=z;}public void setYaw(double value){yaw=value;}public void setPitch(double value){pitch=Math.max(-89,Math.min(89,value));}public void setGrounded(boolean value){grounded=value;}
    @Override public double health(){return health;}@Override public double hunger(){return hunger;}@Override public double hydration(){return hydration;}@Override public double exposure(){return exposure;}@Override public void setHealth(double value){health=clamp(value,0,20);}@Override public void setHunger(double value){hunger=clamp(value,0,20);}@Override public void setHydration(double value){hydration=clamp(value,0,20);}@Override public void setExposure(double value){exposure=clamp(value,0,100);}@Override public int itemCount(ResourceId id){return inventory.count(id);}@Override public boolean consumeItem(ResourceId id,int count){return inventory.consume(id,count);}@Override public int addItem(ResourceId id,int count){return inventory.add(id,count);}
    public double eyeY(){return y+EYE_HEIGHT;}public boolean alive(){return health>0;}private static double clamp(double v,double min,double max){return Math.max(min,Math.min(max,v));}
}
