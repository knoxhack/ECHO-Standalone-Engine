package dev.echo.engine.api;

public interface ContentRegistries {
    Registry<BlockDefinition> blocks();
    Registry<ItemDefinition> items();
    Registry<RecipeDefinition> recipes();
    Registry<EntityDefinition> entities();
    Registry<WorldGeneratorDefinition> worldGenerators();
}
