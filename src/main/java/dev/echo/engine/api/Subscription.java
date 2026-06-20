package dev.echo.engine.api;

@FunctionalInterface
public interface Subscription extends AutoCloseable {
    @Override void close();
}
