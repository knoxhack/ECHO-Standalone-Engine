package dev.echo.engine.runtime.module;

import dev.echo.engine.api.ServiceRegistry;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class DefaultServiceRegistry implements ServiceRegistry {
    private final ConcurrentHashMap<Class<?>, CopyOnWriteArrayList<Object>> services = new ConcurrentHashMap<>();

    public <T> void register(Class<T> type, T service) {
        if (type == null || service == null) throw new IllegalArgumentException("service type/value required");
        if (!type.isInstance(service)) throw new IllegalArgumentException("service does not implement " + type.getName());
        services.computeIfAbsent(type, ignored -> new CopyOnWriteArrayList<>()).add(service);
    }

    public <T> boolean unregister(Class<T> type, T service) {
        CopyOnWriteArrayList<Object> values = services.get(type);
        if (values == null) return false;
        boolean removed = values.remove(service);
        if (values.isEmpty()) services.remove(type, values);
        return removed;
    }

    @Override
    public <T> List<T> all(Class<T> type) {
        CopyOnWriteArrayList<Object> values = services.get(type);
        if (values == null) return List.of();
        return values.stream().map(type::cast).toList();
    }

    @Override
    public <T> Optional<T> first(Class<T> type) {
        return all(type).stream().findFirst();
    }
}
