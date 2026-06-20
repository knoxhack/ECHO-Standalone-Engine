package dev.echo.engine.api;

import java.util.Collection;
import java.util.Optional;

public interface Registry<T> {
    void register(ResourceId id, T value);
    Optional<T> find(ResourceId id);
    T require(ResourceId id);
    Collection<T> values();
    Collection<ResourceId> ids();
    boolean frozen();
}
