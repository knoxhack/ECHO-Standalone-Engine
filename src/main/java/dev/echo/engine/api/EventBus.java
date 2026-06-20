package dev.echo.engine.api;

import java.util.function.Consumer;

public interface EventBus {
    <T> Subscription subscribe(Class<T> eventType, Consumer<T> listener);
    void publish(Object event);
}
