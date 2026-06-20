package dev.echo.engine.runtime.registry;

import dev.echo.engine.api.Registry;
import dev.echo.engine.api.ResourceId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Deterministic insertion-ordered registry. Runtime IDs are assigned only when the pack boot freezes. */
public final class RuntimeRegistry<T> implements Registry<T> {
    private final String name;
    private final LinkedHashMap<ResourceId, T> entries = new LinkedHashMap<>();
    private Map<ResourceId, Integer> runtimeIds = Map.of();
    private List<T> runtimeValues = List.of();
    private boolean frozen;

    public RuntimeRegistry(String name) {
        this.name = name;
    }

    @Override
    public synchronized void register(ResourceId id, T value) {
        if (frozen) throw new IllegalStateException(name + " registry is frozen");
        if (id == null || value == null) throw new IllegalArgumentException(name + " id/value must not be null");
        if (entries.putIfAbsent(id, value) != null) throw new IllegalArgumentException("Duplicate " + name + " id: " + id);
    }

    public synchronized boolean unregister(ResourceId id, T expectedValue) {
        if (frozen) throw new IllegalStateException(name + " registry is frozen");
        T current = entries.get(id);
        if (current != expectedValue) return false;
        entries.remove(id);
        return true;
    }

    @Override
    public synchronized Optional<T> find(ResourceId id) {
        return Optional.ofNullable(entries.get(id));
    }

    @Override
    public synchronized T require(ResourceId id) {
        T value = entries.get(id);
        if (value == null) throw new IllegalArgumentException("Unknown " + name + ": " + id);
        return value;
    }

    @Override
    public synchronized Collection<T> values() {
        return List.copyOf(entries.values());
    }

    @Override
    public synchronized Collection<ResourceId> ids() {
        return List.copyOf(entries.keySet());
    }

    @Override
    public synchronized boolean frozen() {
        return frozen;
    }

    public synchronized void freeze() {
        if (frozen) return;
        LinkedHashMap<ResourceId, Integer> ids = new LinkedHashMap<>();
        ArrayList<T> values = new ArrayList<>();
        int index = 0;
        for (Map.Entry<ResourceId, T> entry : entries.entrySet()) {
            ids.put(entry.getKey(), index++);
            values.add(entry.getValue());
        }
        runtimeIds = Collections.unmodifiableMap(ids);
        runtimeValues = List.copyOf(values);
        frozen = true;
    }

    public int runtimeId(ResourceId id) {
        Integer value = runtimeIds.get(id);
        if (value == null) throw new IllegalArgumentException("Unknown frozen " + name + ": " + id);
        return value;
    }

    public T byRuntimeId(int id) {
        if (id < 0 || id >= runtimeValues.size()) throw new IllegalArgumentException("Invalid " + name + " runtime id: " + id);
        return runtimeValues.get(id);
    }

    public synchronized int size() {
        return entries.size();
    }
}
