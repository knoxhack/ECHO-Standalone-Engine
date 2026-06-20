package dev.echo.engine.game;

import dev.echo.engine.api.*;
import dev.echo.engine.world.Raycaster;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Optional;

public final class InteractionSystem {
    private BlockHit target;private int breakingX=Integer.MIN_VALUE,breakingY,breakingZ;private double breakSeconds,breakProgress;
    public void update(GameSession session,InputState input,double dt){Player player=session.playerEntity();double yaw=Math.toRadians(player.yaw()),pitch=Math.toRadians(player.pitch());double dx=Math.sin(yaw)*Math.cos(pitch),dy=Math.sin(pitch),dz=Math.cos(yaw)*Math.cos(pitch);Optional<BlockHit> cast=Raycaster.cast(session.voxelWorld(),player.x(),player.eyeY(),player.z(),dx,dy,dz,6.0);target=cast.orElse(null);
        if(input.mouseDown(MouseEvent.BUTTON1)){if(input.consumeMouse(MouseEvent.BUTTON1)){String entityHit=session.attackEntity();if(!entityHit.isBlank()){session.message(entityHit);session.audio().play("entity.hit");resetBreak();return;}if(target!=null){InteractionResult result=session.invokeInteraction(target,InteractionAction.PRIMARY);if(result.handled()){session.message(result.message());resetBreak();return;}}}if(target==null){resetBreak();return;}boolean fresh=target.x()!=breakingX||target.y()!=breakingY||target.z()!=breakingZ;if(fresh){breakingX=target.x();breakingY=target.y();breakingZ=target.z();breakSeconds=breakProgress=0;}BlockDefinition block=session.blockDefinition(target.blockId());double required=Math.max(0.12,block.hardness()*0.55);breakSeconds+=dt;breakProgress=Math.min(1,breakSeconds/required);if(breakProgress>=1){session.voxelWorld().setBlock(target.x(),target.y(),target.z(),ResourceId.parse("echo:air"));String drop=block.properties().getOrDefault("drop",block.id().toString());ResourceId dropId=ResourceId.parse(drop);if(session.itemDefinition(dropId)!=null)player.inventory().add(dropId,1);session.audio().play("block.break");session.message("Broke "+block.displayName());resetBreak();}}
        else resetBreak();
        if(input.consumeMouse(MouseEvent.BUTTON3)&&target!=null){InteractionResult result=session.invokeInteraction(target,InteractionAction.SECONDARY);if(result.handled()){session.message(result.message());session.audio().play("ui.interact");}else placeSelected(session,target);}
        if(input.consumeKey(KeyEvent.VK_F)){InteractionResult result=session.invokeInteraction(target,InteractionAction.USE_ITEM);if(result.handled()){session.message(result.message());}else useSelected(session);}
        if(input.consumeKey(KeyEvent.VK_C))session.craftFirstAvailable();
    }
    private void placeSelected(GameSession session,BlockHit hit){ItemStack stack=session.playerEntity().inventory().selectedStack();if(stack.emptyStack())return;ItemDefinition item=session.itemDefinition(stack.itemId());if(item==null||item.placesBlock()==null)return;double minX=session.playerEntity().x()-Player.WIDTH/2,maxX=session.playerEntity().x()+Player.WIDTH/2,minY=session.playerEntity().y(),maxY=minY+Player.HEIGHT,minZ=session.playerEntity().z()-Player.WIDTH/2,maxZ=session.playerEntity().z()+Player.WIDTH/2;boolean intersects=hit.adjacentX()+1>minX&&hit.adjacentX()<maxX&&hit.adjacentY()+1>minY&&hit.adjacentY()<maxY&&hit.adjacentZ()+1>minZ&&hit.adjacentZ()<maxZ;if(intersects)return;if(session.voxelWorld().setBlock(hit.adjacentX(),hit.adjacentY(),hit.adjacentZ(),item.placesBlock())){stack.remove(1);session.audio().play("block.place");session.message("Placed "+session.blockDefinition(item.placesBlock()).displayName());}}
    private void useSelected(GameSession session){ItemStack stack=session.playerEntity().inventory().selectedStack();if(stack.emptyStack())return;ItemDefinition item=session.itemDefinition(stack.itemId());if(item==null||(item.foodRestore()<=0&&item.hydrationRestore()<=0))return;session.playerEntity().setHunger(session.playerEntity().hunger()+item.foodRestore());session.playerEntity().setHydration(session.playerEntity().hydration()+item.hydrationRestore());stack.remove(1);session.audio().play("item.consume");session.message("Used "+item.displayName());}
    private void resetBreak(){breakingX=Integer.MIN_VALUE;breakSeconds=breakProgress=0;}public BlockHit target(){return target;}public double breakProgress(){return breakProgress;}
}
