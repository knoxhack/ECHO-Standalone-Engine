package dev.echo.engine.api;

public interface EchoModule {
    void onLoad(EchoModuleContext context) throws Exception;
    default void onUnload() throws Exception { }
}
