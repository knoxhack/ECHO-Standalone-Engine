package dev.echo.engine.runtime.module;

import dev.echo.engine.api.EventBus;
import dev.echo.engine.api.Subscription;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class DefaultEventBus implements EventBus {
    private final ConcurrentHashMap<Class<?>,CopyOnWriteArrayList<Consumer<Object>>> listeners=new ConcurrentHashMap<>();
    @Override public <T> Subscription subscribe(Class<T> type, Consumer<T> listener){
        Consumer<Object> raw=value->listener.accept(type.cast(value));listeners.computeIfAbsent(type,k->new CopyOnWriteArrayList<>()).add(raw);
        return ()->listeners.getOrDefault(type,new CopyOnWriteArrayList<>()).remove(raw);
    }
    @Override public void publish(Object event){if(event==null)return;listeners.forEach((type,list)->{if(type.isInstance(event))for(Consumer<Object> listener:list)listener.accept(event);});}
}
