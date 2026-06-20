package dev.echo.engine.api;

public interface ModuleLogger {
    void info(String message);
    void warn(String message);
    void error(String message, Throwable failure);
}
