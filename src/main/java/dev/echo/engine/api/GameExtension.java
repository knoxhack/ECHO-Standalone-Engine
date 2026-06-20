package dev.echo.engine.api;

import java.util.List;

public interface GameExtension {
    default void onSessionStart(GameAccess game) { }
    default void tick(GameAccess game, double deltaSeconds) { }
    default InteractionResult interact(GameAccess game, BlockHit hit, InteractionAction action) { return InteractionResult.pass(); }
    default List<String> hudLines(GameAccess game) { return List.of(); }
}
