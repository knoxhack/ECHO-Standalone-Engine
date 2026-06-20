package dev.echo.engine.runtime.adaptercore;

import dev.echo.engine.api.BlockDefinition;
import dev.echo.engine.api.EntityDefinition;
import dev.echo.engine.api.GameExtension;
import dev.echo.engine.api.ItemDefinition;
import dev.echo.engine.api.ModuleDescriptor;
import dev.echo.engine.api.RecipeDefinition;
import dev.echo.engine.api.ResourceId;
import dev.echo.engine.api.WorldGenerator;
import dev.echo.engine.api.WorldGeneratorDefinition;
import dev.echo.engine.api.Subscription;
import dev.echo.engine.api.adapter.AdapterCoreSession;
import dev.echo.engine.api.adapter.MutationReceipt;
import dev.echo.engine.api.adapter.MutationStatus;
import dev.echo.engine.api.graph.GraphNodeView;
import dev.echo.engine.runtime.content.CanonicalContentMap;
import dev.echo.engine.runtime.content.ExportBinding;
import dev.echo.engine.runtime.module.DefaultEventBus;
import dev.echo.engine.runtime.module.DefaultServiceRegistry;
import dev.echo.engine.runtime.registry.DefaultContentRegistries;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Canonical runtime bridge for graph-backed content and executable module mutations.
 * No installed module receives direct registry mutation access.
 */
public final class AdapterCoreRuntime implements AutoCloseable {
    private final CanonicalContentMap graph;
    private final DefaultContentRegistries registries;
    private final DefaultServiceRegistry services;
    private final DefaultEventBus events;
    private final AtomicLong sequence = new AtomicLong();
    private final ArrayList<MutationReceipt> receipts = new ArrayList<>();
    private final LinkedHashMap<String, ArrayList<Runnable>> rollbackByModule = new LinkedHashMap<>();
    private boolean frozen;
    private boolean closed;

    public AdapterCoreRuntime(
            CanonicalContentMap graph,
            DefaultContentRegistries registries,
            DefaultServiceRegistry services,
            DefaultEventBus events
    ) {
        this.graph = java.util.Objects.requireNonNull(graph, "graph");
        this.registries = java.util.Objects.requireNonNull(registries, "registries");
        this.services = java.util.Objects.requireNonNull(services, "services");
        this.events = java.util.Objects.requireNonNull(events, "events");
    }

    /** Materializes declarative graph nodes into runtime definitions through AdapterCore receipts. */
    public void materialize(ModuleDescriptor descriptor) {
        ensureOpen();
        List<GraphNodeView> nodes = graph.nodes().values().stream()
                .filter(node -> node.moduleId().equals(descriptor.id()))
                .sorted(Comparator.comparing(GraphNodeView::id))
                .toList();
        for (GraphNodeView node : nodes) {
            try {
                switch (node.kind()) {
                    case "BLOCK" -> registerBlock(descriptor, node, GraphDefinitionFactory.block(graph, node));
                    case "ITEM" -> registerItem(descriptor, node, GraphDefinitionFactory.item(graph, node));
                    case "RECIPE" -> registerRecipe(descriptor, node, GraphDefinitionFactory.recipe(graph, node));
                    case "ENTITY" -> registerEntity(descriptor, node, GraphDefinitionFactory.entity(graph, node));
                    default -> {
                        // Semantic-only nodes remain in the canonical map and are consumed by runtime systems/entrypoints.
                    }
                }
            } catch (RuntimeException failure) {
                reject(
                        descriptor.id(),
                        node.id(),
                        domainForKind(node.kind()),
                        "graph.materialize." + node.kind().toLowerCase(java.util.Locale.ROOT),
                        failure.getMessage(),
                        Map.of("kind", node.kind())
                );
                rollbackModule(descriptor.id());
                throw failure;
            }
        }
    }

    public AdapterCoreSession session(ModuleDescriptor descriptor) {
        ensureOpen();
        return new Session(descriptor);
    }

    public synchronized void freeze() {
        ensureOpen();
        if (frozen) return;
        AdapterCoreAudit audit = audit();
        if (!audit.ready()) {
            throw new IllegalStateException("AdapterCore has rejected mutations: " + audit.rejected());
        }
        registries.freeze();
        frozen = true;
    }

    public synchronized List<MutationReceipt> receipts() {
        return List.copyOf(receipts);
    }

    public synchronized List<MutationReceipt> receipts(String moduleId) {
        return receipts.stream().filter(receipt -> receipt.moduleId().equals(moduleId)).toList();
    }

    public synchronized AdapterCoreAudit audit() {
        LinkedHashMap<String, Integer> domains = new LinkedHashMap<>();
        int accepted = 0;
        int rejected = 0;
        int revoked = 0;
        for (MutationReceipt receipt : receipts) {
            switch (receipt.status()) {
                case ACCEPTED -> {
                    accepted++;
                    domains.merge(receipt.domain(), 1, Integer::sum);
                }
                case REJECTED -> rejected++;
                case REVOKED -> revoked++;
            }
        }
        return new AdapterCoreAudit(rejected == 0, accepted, rejected, revoked, domains);
    }

    public CanonicalContentMap graph() {
        return graph;
    }

    private void registerBlock(ModuleDescriptor descriptor, GraphNodeView node, BlockDefinition definition) {
        validateGraphBackedMutation(descriptor, node, "BLOCK");
        registries.runtimeBlocks().register(definition.id(), definition);
        addRollback(descriptor.id(), () -> registries.runtimeBlocks().unregister(definition.id(), definition));
        accept(descriptor.id(), node.id(), "blocks", "graph.register.block", Map.of("runtimeId", definition.id().toString()));
    }

    private void registerItem(ModuleDescriptor descriptor, GraphNodeView node, ItemDefinition definition) {
        validateGraphBackedMutation(descriptor, node, "ITEM");
        registries.runtimeItems().register(definition.id(), definition);
        addRollback(descriptor.id(), () -> registries.runtimeItems().unregister(definition.id(), definition));
        accept(descriptor.id(), node.id(), "items", "graph.register.item", Map.of("runtimeId", definition.id().toString()));
    }

    private void registerRecipe(ModuleDescriptor descriptor, GraphNodeView node, RecipeDefinition definition) {
        validateGraphBackedMutation(descriptor, node, "RECIPE");
        registries.runtimeRecipes().register(definition.id(), definition);
        addRollback(descriptor.id(), () -> registries.runtimeRecipes().unregister(definition.id(), definition));
        accept(descriptor.id(), node.id(), "recipes", "graph.register.recipe", Map.of("runtimeId", definition.id().toString()));
    }

    private void registerEntity(ModuleDescriptor descriptor, GraphNodeView node, EntityDefinition definition) {
        validateGraphBackedMutation(descriptor, node, "ENTITY");
        registries.runtimeEntities().register(definition.id(), definition);
        addRollback(descriptor.id(), () -> registries.runtimeEntities().unregister(definition.id(), definition));
        accept(descriptor.id(), node.id(), "entities", "graph.register.entity", Map.of("runtimeId", definition.id().toString()));
    }

    private void validateGraphBackedMutation(
            ModuleDescriptor descriptor,
            GraphNodeView node,
            String expectedKind
    ) {
        ensureMutable();
        validateOwnership(descriptor, node);
        if (!node.kind().equals(expectedKind)) {
            throw new IllegalArgumentException("Node " + node.id() + " is " + node.kind() + ", expected " + expectedKind);
        }
        graph.requireBinding(node.id(), CanonicalContentMap.STANDALONE_TARGET);
    }

    private synchronized MutationReceipt accept(
            String moduleId,
            String nodeId,
            String domain,
            String mutationId,
            Map<String, String> details
    ) {
        MutationReceipt receipt = new MutationReceipt(
                sequence.incrementAndGet(),
                mutationId,
                moduleId,
                nodeId,
                domain,
                MutationStatus.ACCEPTED,
                "accepted",
                Instant.now(),
                details
        );
        receipts.add(receipt);
        return receipt;
    }

    private synchronized MutationReceipt reject(
            String moduleId,
            String nodeId,
            String domain,
            String mutationId,
            String message,
            Map<String, String> details
    ) {
        MutationReceipt receipt = new MutationReceipt(
                sequence.incrementAndGet(),
                mutationId,
                moduleId,
                nodeId,
                domain,
                MutationStatus.REJECTED,
                message == null ? "rejected" : message,
                Instant.now(),
                details
        );
        receipts.add(receipt);
        return receipt;
    }

    private synchronized void addRollback(String moduleId, Runnable action) {
        rollbackByModule.computeIfAbsent(moduleId, ignored -> new ArrayList<>()).add(action);
    }

    public synchronized void rollbackModule(String moduleId) {
        if (frozen) throw new IllegalStateException("Cannot roll back frozen runtime registries");
        ArrayList<Runnable> actions = rollbackByModule.remove(moduleId);
        if (actions == null) return;
        for (int index = actions.size() - 1; index >= 0; index--) {
            actions.get(index).run();
        }
        receipts.add(new MutationReceipt(
                sequence.incrementAndGet(),
                "adaptercore.rollback.module",
                moduleId,
                "",
                "lifecycle",
                MutationStatus.REVOKED,
                "rolled back pre-freeze module mutations",
                Instant.now(),
                Map.of("actionCount", Integer.toString(actions.size()))
        ));
    }

    private void validateOwnership(ModuleDescriptor descriptor, GraphNodeView node) {
        if (!node.moduleId().equals(descriptor.id())) {
            throw new SecurityException(
                    descriptor.id() + " cannot mutate graph node owned by " + node.moduleId() + ": " + node.id()
            );
        }
    }

    private void ensureMutable() {
        ensureOpen();
        if (frozen) throw new IllegalStateException("AdapterCore runtime is frozen");
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("AdapterCore runtime is closed");
    }

    private static String domainForKind(String kind) {
        return switch (kind) {
            case "BLOCK" -> "blocks";
            case "ITEM" -> "items";
            case "RECIPE" -> "recipes";
            case "ENTITY" -> "entities";
            default -> "content";
        };
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
    }

    private final class Session implements AdapterCoreSession {
        private static final Set<String> GAMEPLAY_KINDS = Set.of(
                "GAMEPLAY_SYSTEM", "SURVIVAL_PROFILE", "HAZARD", "WEATHER_PROFILE", "MISSION"
        );
        private static final Set<String> SERVICE_KINDS = Set.of("SERVICE", "CAPABILITY", "UI_SCREEN");
        private final ModuleDescriptor descriptor;

        private Session(ModuleDescriptor descriptor) {
            this.descriptor = descriptor;
        }

        @Override
        public MutationReceipt bindWorldGenerator(String graphNodeId, int priority, WorldGenerator generator) {
            if (generator == null) return reject(
                    descriptor.id(), graphNodeId, "worldgen", "adaptercore.bind.world_generator",
                    "world generator must not be null", Map.of()
            );
            try {
                ensureMutable();
                GraphNodeView node = graph.requireNode(graphNodeId);
                validateOwnership(descriptor, node);
                if (!node.kind().equals("WORLD_GENERATOR")) {
                    throw new IllegalArgumentException("Expected WORLD_GENERATOR node, got " + node.kind());
                }
                if (!descriptor.canMutate("adaptercore.worldgen")) {
                    throw new SecurityException(descriptor.id() + " lacks adaptercore.worldgen");
                }
                ExportBinding binding = graph.requireBinding(graphNodeId, CanonicalContentMap.STANDALONE_TARGET);
                ResourceId runtimeId = ResourceId.parse(binding.runtimeId());
                WorldGeneratorDefinition definition = new WorldGeneratorDefinition(runtimeId, priority, generator);
                registries.runtimeWorldGenerators().register(runtimeId, definition);
                addRollback(descriptor.id(), () -> registries.runtimeWorldGenerators().unregister(runtimeId, definition));
                return accept(
                        descriptor.id(), graphNodeId, "worldgen", "adaptercore.bind.world_generator",
                        Map.of("runtimeId", runtimeId.toString(), "priority", Integer.toString(priority))
                );
            } catch (RuntimeException failure) {
                return reject(
                        descriptor.id(), graphNodeId, "worldgen", "adaptercore.bind.world_generator",
                        failure.getMessage(), Map.of()
                );
            }
        }

        @Override
        public MutationReceipt bindGameExtension(String graphNodeId, GameExtension extension) {
            if (extension == null) return reject(
                    descriptor.id(), graphNodeId, "gameplay", "adaptercore.bind.game_extension",
                    "game extension must not be null", Map.of()
            );
            try {
                ensureMutable();
                GraphNodeView node = graph.requireNode(graphNodeId);
                validateOwnership(descriptor, node);
                if (!GAMEPLAY_KINDS.contains(node.kind())) {
                    throw new IllegalArgumentException("Node kind cannot host gameplay extension: " + node.kind());
                }
                if (!descriptor.canMutate("adaptercore.gameplay")) {
                    throw new SecurityException(descriptor.id() + " lacks adaptercore.gameplay");
                }
                graph.requireBinding(graphNodeId, CanonicalContentMap.STANDALONE_TARGET);
                services.register(GameExtension.class, extension);
                addRollback(descriptor.id(), () -> services.unregister(GameExtension.class, extension));
                return accept(
                        descriptor.id(), graphNodeId, "gameplay", "adaptercore.bind.game_extension",
                        Map.of("implementation", extension.getClass().getName())
                );
            } catch (RuntimeException failure) {
                return reject(
                        descriptor.id(), graphNodeId, "gameplay", "adaptercore.bind.game_extension",
                        failure.getMessage(), Map.of()
                );
            }
        }

        @Override
        public <T> MutationReceipt publishService(String graphNodeId, Class<T> serviceType, T service) {
            if (serviceType == null || service == null) return reject(
                    descriptor.id(), graphNodeId, "services", "adaptercore.publish.service",
                    "service type/value required", Map.of()
            );
            try {
                ensureMutable();
                GraphNodeView node = graph.requireNode(graphNodeId);
                validateOwnership(descriptor, node);
                if (!SERVICE_KINDS.contains(node.kind())) {
                    throw new IllegalArgumentException("Node kind cannot publish a service: " + node.kind());
                }
                if (!descriptor.canMutate("adaptercore.services")) {
                    throw new SecurityException(descriptor.id() + " lacks adaptercore.services");
                }
                graph.requireBinding(graphNodeId, CanonicalContentMap.STANDALONE_TARGET);
                services.register(serviceType, service);
                addRollback(descriptor.id(), () -> services.unregister(serviceType, service));
                return accept(
                        descriptor.id(), graphNodeId, "services", "adaptercore.publish.service",
                        Map.of("serviceType", serviceType.getName())
                );
            } catch (RuntimeException failure) {
                return reject(
                        descriptor.id(), graphNodeId, "services", "adaptercore.publish.service",
                        failure.getMessage(), Map.of()
                );
            }
        }

        @Override
        public <T> MutationReceipt subscribeEvent(
                String graphNodeId,
                Class<T> eventType,
                Consumer<T> listener
        ) {
            if (eventType == null || listener == null) {
                return reject(
                        descriptor.id(), graphNodeId, "events", "adaptercore.subscribe.event",
                        "event type/listener required", Map.of()
                );
            }
            try {
                ensureMutable();
                GraphNodeView node = graph.requireNode(graphNodeId);
                validateOwnership(descriptor, node);
                if (!GAMEPLAY_KINDS.contains(node.kind()) && !SERVICE_KINDS.contains(node.kind())) {
                    throw new IllegalArgumentException("Node kind cannot host an event listener: " + node.kind());
                }
                if (!descriptor.canMutate("adaptercore.events")) {
                    throw new SecurityException(descriptor.id() + " lacks adaptercore.events");
                }
                graph.requireBinding(graphNodeId, CanonicalContentMap.STANDALONE_TARGET);
                Subscription subscription = events.subscribe(eventType, listener);
                addRollback(descriptor.id(), subscription::close);
                return accept(
                        descriptor.id(), graphNodeId, "events", "adaptercore.subscribe.event",
                        Map.of("eventType", eventType.getName())
                );
            } catch (RuntimeException failure) {
                return reject(
                        descriptor.id(), graphNodeId, "events", "adaptercore.subscribe.event",
                        failure.getMessage(), Map.of()
                );
            }
        }

        @Override
        public List<MutationReceipt> receipts() {
            return AdapterCoreRuntime.this.receipts(descriptor.id());
        }
    }
}
