package dev.echo.engine.api;

public interface GameAccess {
    PlayerAccess player();
    WorldAccess world();
    ModuleState state(String moduleId);
    GameAudio audio();
    long seed();
    double timeSeconds();
    void message(String text);
}
