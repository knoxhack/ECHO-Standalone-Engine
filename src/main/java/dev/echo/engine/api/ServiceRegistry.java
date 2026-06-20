package dev.echo.engine.api;

import java.util.List;
import java.util.Optional;

/** Read-only view of services already published through AdapterCore. */
public interface ServiceRegistry {
    <T> List<T> all(Class<T> type);

    <T> Optional<T> first(Class<T> type);
}
