package dev.echo.engine.game;

import dev.echo.engine.api.EntityDefinition;
import java.util.UUID;

public final class WorldEntity {
    private final UUID id=UUID.randomUUID();private final EntityDefinition definition;private double x,y,z,health,attackCooldown;
    public WorldEntity(EntityDefinition definition,double x,double y,double z){this.definition=definition;this.x=x;this.y=y;this.z=z;this.health=definition.maxHealth();}
    public UUID id(){return id;}public EntityDefinition definition(){return definition;}public double x(){return x;}public double y(){return y;}public double z(){return z;}public double health(){return health;}public double attackCooldown(){return attackCooldown;}public void setPosition(double x,double y,double z){this.x=x;this.y=y;this.z=z;}public void damage(double amount){health=Math.max(0,health-Math.max(0,amount));}public boolean alive(){return health>0;}public void tickCooldown(double dt){attackCooldown=Math.max(0,attackCooldown-dt);}public void resetAttackCooldown(){attackCooldown=1.0;}
}
