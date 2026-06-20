package dev.echo.engine.runtime.registry;

import dev.echo.engine.api.BlockDefinition;
import dev.echo.engine.api.ContentRegistries;
import dev.echo.engine.api.ItemDefinition;
import dev.echo.engine.api.EntityDefinition;
import dev.echo.engine.api.RecipeDefinition;
import dev.echo.engine.api.Registry;
import dev.echo.engine.api.ResourceId;
import dev.echo.engine.api.WorldGeneratorDefinition;

import java.util.Map;

public final class DefaultContentRegistries implements ContentRegistries {
    private final RuntimeRegistry<BlockDefinition> blocks=new RuntimeRegistry<>("block");
    private final RuntimeRegistry<ItemDefinition> items=new RuntimeRegistry<>("item");
    private final RuntimeRegistry<RecipeDefinition> recipes=new RuntimeRegistry<>("recipe");
    private final RuntimeRegistry<EntityDefinition> entities=new RuntimeRegistry<>("entity");
    private final RuntimeRegistry<WorldGeneratorDefinition> worldGenerators=new RuntimeRegistry<>("world generator");
    public DefaultContentRegistries(){
        blocks.register(ResourceId.parse("echo:air"),new BlockDefinition(ResourceId.parse("echo:air"),"Air",0x00000000,false,false,0,0,0,Map.of()));
        blocks.register(ResourceId.parse("echo:missing_block"),new BlockDefinition(ResourceId.parse("echo:missing_block"),"Missing Block",0xFFFF00FF,true,true,0.1,0,0,Map.of("diagnostic","true")));
    }
    @Override public Registry<BlockDefinition> blocks(){return blocks;}
    @Override public Registry<ItemDefinition> items(){return items;}
    @Override public Registry<RecipeDefinition> recipes(){return recipes;}
    @Override public Registry<EntityDefinition> entities(){return entities;}
    @Override public Registry<WorldGeneratorDefinition> worldGenerators(){return worldGenerators;}
    public RuntimeRegistry<BlockDefinition> runtimeBlocks(){return blocks;}
    public RuntimeRegistry<ItemDefinition> runtimeItems(){return items;}
    public RuntimeRegistry<RecipeDefinition> runtimeRecipes(){return recipes;}
    public RuntimeRegistry<EntityDefinition> runtimeEntities(){return entities;}
    public RuntimeRegistry<WorldGeneratorDefinition> runtimeWorldGenerators(){return worldGenerators;}
    public void freeze(){blocks.freeze();items.freeze();recipes.freeze();entities.freeze();worldGenerators.freeze();}
}
