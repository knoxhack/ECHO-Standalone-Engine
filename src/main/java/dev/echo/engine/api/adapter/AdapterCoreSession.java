package dev.echo.engine.api.adapter;

import dev.echo.engine.api.GameExtension;
import dev.echo.engine.api.WorldGenerator;

import java.util.List;
import java.util.function.Consumer;

/**
 * Module-scoped AdapterCore bridge. Every executable runtime mutation is tied to a canonical graph node,
 * validated against the module descriptor, and recorded as a receipt.
 */
public interface AdapterCoreSession {
    MutationReceipt bindWorldGenerator(String graphNodeId, int priority, WorldGenerator generator);

    MutationReceipt bindGameExtension(String graphNodeId, GameExtension extension);

    <T> MutationReceipt publishService(String graphNodeId, Class<T> serviceType, T service);

    <T> MutationReceipt subscribeEvent(String graphNodeId, Class<T> eventType, Consumer<T> listener);

    List<MutationReceipt> receipts();
}
