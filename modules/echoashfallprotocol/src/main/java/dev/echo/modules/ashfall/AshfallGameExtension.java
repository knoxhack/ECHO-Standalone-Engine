package dev.echo.modules.ashfall;

import dev.echo.engine.api.BlockHit;
import dev.echo.engine.api.GameAccess;
import dev.echo.engine.api.GameExtension;
import dev.echo.engine.api.InteractionAction;
import dev.echo.engine.api.InteractionResult;
import dev.echo.engine.api.ModuleState;
import dev.echo.engine.api.PlayerAccess;
import dev.echo.engine.api.ResourceId;
import dev.echo.engine.api.graph.ContentGraphView;
import dev.echo.engine.api.graph.GraphEdgeView;
import dev.echo.engine.api.graph.GraphNodeView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ashfall behavior adapter. Stable graph node IDs select contracts; all runtime IDs, content values,
 * tuning, objective labels, starter inventory, machine IO, and loot come from the installed graph/export data.
 */
final class AshfallGameExtension implements GameExtension {
    private static final String MODULE_ID = "echoashfallprotocol";
    private static final String MISSION_NODE = "echoashfallprotocol:mission/crash_protocol";
    private static final String STARTER_NODE = "echoworldstarter:service/starter_loadout";
    private static final String HAZARD_NODE = "echoashfallprotocol:hazard/toxic_ash";
    private static final String WEATHER_NODE = "echoashfallprotocol:weather/ash_storm";
    private static final String SHELTER_NODE = "echoashfallprotocol:block/ash_campfire";
    private static final String TERMINAL_NODE = "echoterminal:block/echo_terminal";
    private static final String CACHE_NODE = "echoashfallprotocol:block/echo_cache";
    private static final String POWER_NODE = "echoashfallprotocol:block/power_node";

    private final ContentGraphView graph;
    private final AshfallRuntimeIds ids;
    private final AshfallTuning tuning;
    private final List<String> objectives;
    private final Map<ResourceId, MachinePlan> machinesByBlock;
    private final List<ResourceId> toxicSources;

    AshfallGameExtension(ContentGraphView graph, AshfallRuntimeIds ids, AshfallTuning tuning) {
        this.graph = graph;
        this.ids = ids;
        this.tuning = tuning;
        objectives = graph.outgoing(MISSION_NODE, "mission_has_objective").stream()
                .sorted(Comparator.comparingInt(edge -> edge.integer("order", Integer.MAX_VALUE)))
                .map(GraphEdgeView::target)
                .toList();
        if (objectives.isEmpty()) throw new IllegalArgumentException(MISSION_NODE + " has no objectives");
        machinesByBlock = buildMachinePlans();
        toxicSources = graph.outgoing(HAZARD_NODE, "hazard_emitted_by_block").stream()
                .map(GraphEdgeView::target)
                .map(graph::requireRuntimeId)
                .map(ResourceId::parse)
                .toList();
        if (toxicSources.isEmpty()) throw new IllegalArgumentException(HAZARD_NODE + " has no sourceBlockNodeIds");
    }

    @Override
    public void onSessionStart(GameAccess game) {
        ModuleState state = game.state(MODULE_ID);
        if (!state.getBoolean("starterGranted", false)) {
            grantStarterLoadout(game);
            state.putBoolean("starterGranted", true);
            game.message(graph.requireNode(MISSION_NODE).displayName() + " active. " + objectiveLabel(objectives.get(0)));
        }
        if (state.get("objectiveNodeId", "").isBlank()) state.put("objectiveNodeId", objectives.get(0));
    }

    @Override
    public void tick(GameAccess game, double deltaSeconds) {
        double dt = Math.max(0.0, deltaSeconds);
        PlayerAccess player = game.player();
        ModuleState state = game.state(MODULE_ID);
        player.setHunger(player.hunger() - dt * tuning.hungerDrainPerSecond());
        player.setHydration(player.hydration() - dt * tuning.hydrationDrainPerSecond());

        boolean sheltered = game.world().isBlockNear(
                ids.shelter(), player.x(), player.y(), player.z(), tuning.shelterRadius()
        );
        boolean toxic = toxicSources.stream().anyMatch(block -> game.world().isBlockNear(
                block, player.x(), player.y(), player.z(), tuning.toxicSourceRadius()
        ));
        boolean storm = stormActive(game.timeSeconds());
        boolean previousStorm = state.getBoolean("stormActive", false);
        if (storm != previousStorm) {
            state.putBoolean("stormActive", storm);
            game.message(storm ? "Ash storm front active" : "Ash storm front passed");
            game.audio().play(storm ? "weather.ash_storm.start" : "weather.ash_storm.stop");
        }

        double exposureRate = toxic ? tuning.toxicExposurePerSecond() : tuning.passiveExposurePerSecond();
        if (storm) exposureRate *= tuning.stormExposureMultiplier();
        if (sheltered) exposureRate -= tuning.shelterRecoveryPerSecond();
        player.setExposure(player.exposure() + exposureRate * dt);

        double damageClock = state.getDouble("damageClock", 0.0) + dt;
        while (damageClock >= tuning.damageIntervalSeconds()) {
            damageClock -= tuning.damageIntervalSeconds();
            if (player.hydration() <= 0.0) player.setHealth(player.health() - tuning.dehydrationDamage());
            if (player.hunger() <= 0.0) player.setHealth(player.health() - tuning.starvationDamage());
            if (player.exposure() >= tuning.criticalExposure()) {
                player.setHealth(player.health() - tuning.criticalDamage());
            }
        }
        state.putDouble("damageClock", damageClock);

        double warningClock = state.getDouble("warningClock", 0.0) + dt;
        if (player.exposure() >= tuning.warningExposure() && warningClock >= tuning.warningIntervalSeconds()) {
            warningClock = 0.0;
            game.message("Ash exposure critical — reach shelter or a cleanser");
            game.audio().play("hazard.ash.warning");
        }
        state.putDouble("warningClock", warningClock);
    }

    @Override
    public InteractionResult interact(GameAccess game, BlockHit hit, InteractionAction action) {
        if (hit == null || action != InteractionAction.SECONDARY) return InteractionResult.pass();
        ResourceId block = hit.blockId();
        if (block.equals(ids.terminal())) return interactTerminal(game);
        if (block.equals(ids.shelter())) return interactShelter(game);
        if (block.equals(ids.cache())) return interactCache(game);
        if (block.equals(ids.powerNode())) return interactPowerNode(game);
        MachinePlan machine = machinesByBlock.get(block);
        return machine == null ? InteractionResult.pass() : runMachine(game, machine);
    }

    @Override
    public List<String> hudLines(GameAccess game) {
        ModuleState state = game.state(MODULE_ID);
        ArrayList<String> lines = new ArrayList<>();
        String objectiveNode = state.get("objectiveNodeId", objectives.get(0));
        lines.add("ASHFALL  •  " + objectiveLabel(objectiveNode));
        if (state.getBoolean("stormActive", false)) lines.add("⚠ ASH STORM ACTIVE");
        if (state.getBoolean("powerRepaired", false)) lines.add("POWER NODE: ONLINE");
        else if (state.getBoolean("cacheRecovered", false)) lines.add("CRASH CACHE: RECOVERED");
        if (game.player().exposure() >= tuning.warningExposure()) {
            lines.add("⚠ ASH EXPOSURE " + (int) game.player().exposure() + "%");
        }
        return List.copyOf(lines);
    }

    private void grantStarterLoadout(GameAccess game) {
        List<GraphEdgeView> grants = graph.outgoing(STARTER_NODE, "service_grants_item");
        if (grants.isEmpty()) throw new IllegalArgumentException(STARTER_NODE + " has no service_grants_item edges");
        for (GraphEdgeView grant : grants) {
            int count = Math.max(1, grant.integer("count", 1));
            int remainder = game.player().addItem(
                    ResourceId.parse(graph.requireRuntimeId(grant.target())), count
            );
            if (remainder != 0) throw new IllegalStateException("Starter inventory overflow for " + grant.target());
        }
    }

    private InteractionResult interactTerminal(GameAccess game) {
        ModuleState state = game.state(MODULE_ID);
        state.putBoolean("terminalOnline", true);
        completeObjective(state, "echoashfallprotocol:objective/inspect_terminal");
        String screen = graph.outgoing(TERMINAL_NODE, "block_opens_ui").stream()
                .findFirst()
                .map(GraphEdgeView::target)
                .map(graph::requireNode)
                .map(GraphNodeView::displayName)
                .orElse("Field Terminal");
        return InteractionResult.handled(screen + ": " + objectiveLabel(state.get("objectiveNodeId", objectives.get(0))));
    }

    private InteractionResult interactShelter(GameAccess game) {
        ModuleState state = game.state(MODULE_ID);
        GraphNodeView shelter = graph.requireNode(SHELTER_NODE);
        game.player().setExposure(game.player().exposure() - shelter.requireDecimal("exposureReduction"));
        state.putBoolean("shelterBuilt", true);
        return InteractionResult.handled(shelter.displayName() + " sealed; exposure reduced");
    }

    private InteractionResult interactCache(GameAccess game) {
        ModuleState state = game.state(MODULE_ID);
        if (state.getBoolean("cacheRecovered", false)) {
            return InteractionResult.handled(graph.requireNode(CACHE_NODE).displayName() + " already recovered");
        }
        String lootNode = graph.outgoing(CACHE_NODE, "block_uses_loot_table").stream()
                .findFirst()
                .map(GraphEdgeView::target)
                .orElseThrow(() -> new IllegalArgumentException(CACHE_NODE + " has no loot table edge"));
        List<GraphEdgeView> drops = graph.outgoing(lootNode, "loot_table_outputs_item");
        if (drops.isEmpty()) throw new IllegalArgumentException(lootNode + " has no loot outputs");
        ArrayList<String> granted = new ArrayList<>();
        for (GraphEdgeView drop : drops) {
            ResourceId itemId = ResourceId.parse(graph.requireRuntimeId(drop.target()));
            int count = Math.max(1, drop.integer("count", 1));
            int remainder = game.player().addItem(itemId, count);
            if (remainder != 0) throw new IllegalStateException("Inventory overflow for loot " + itemId);
            granted.add(count + "× " + graph.requireNode(drop.target()).displayName());
        }
        state.putBoolean("cacheRecovered", true);
        completeObjective(state, "echoashfallprotocol:objective/recover_cache");
        return InteractionResult.handled("Crash cache recovered: " + String.join(", ", granted));
    }

    private InteractionResult interactPowerNode(GameAccess game) {
        ModuleState state = game.state(MODULE_ID);
        GraphNodeView power = graph.requireNode(POWER_NODE);
        if (state.getBoolean("powerRepaired", false)) {
            return InteractionResult.handled(power.displayName() + " stable");
        }
        GraphEdgeView inputEdge = graph.outgoing(POWER_NODE, "block_consumes_item").stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(POWER_NODE + " has no block_consumes_item edge"));
        ResourceId input = ResourceId.parse(graph.requireRuntimeId(inputEdge.target()));
        int inputCount = Math.max(1, inputEdge.integer("count", 1));
        if (!game.player().consumeItem(input, inputCount)) {
            return InteractionResult.handled(power.displayName() + " requires " + inputCount + "× "
                    + graph.requireNode(inputEdge.target()).displayName());
        }
        state.putBoolean("powerRepaired", true);
        completeObjective(state, "echoashfallprotocol:objective/repair_power");
        game.audio().play("machine.power_node.online");
        return InteractionResult.handled(power.displayName() + " repaired — extraction systems responding");
    }

    private InteractionResult runMachine(GameAccess game, MachinePlan machine) {
        for (Map.Entry<ResourceId, Integer> input : machine.inputs().entrySet()) {
            if (game.player().itemCount(input.getKey()) < input.getValue()) {
                return InteractionResult.handled(machine.displayName() + " needs " + input.getValue() + "× "
                        + graph.requireNode(machine.inputNodeIds().get(input.getKey())).displayName());
            }
        }
        machine.inputs().forEach((item, count) -> game.player().consumeItem(item, count));
        if (machine.output() != null) {
            int remainder = game.player().addItem(machine.output(), machine.outputCount());
            if (remainder != 0) throw new IllegalStateException("Inventory overflow for machine output " + machine.output());
        }
        if (machine.exposureReduction() > 0.0) {
            game.player().setExposure(game.player().exposure() - machine.exposureReduction());
        }
        game.audio().play("machine.complete");
        if (machine.output() == null) return InteractionResult.handled(machine.displayName() + " cycle complete");
        return InteractionResult.handled(machine.displayName() + " produced " + machine.outputCount() + "× "
                + graph.requireNode(machine.outputNodeId()).displayName());
    }

    private Map<ResourceId, MachinePlan> buildMachinePlans() {
        LinkedHashMap<ResourceId, MachinePlan> result = new LinkedHashMap<>();
        for (GraphNodeView machine : graph.nodesByKind("MACHINE")) {
            String blockNodeId = graph.outgoing(machine.id(), "machine_uses_block").stream()
                    .findFirst()
                    .map(GraphEdgeView::target)
                    .orElseThrow(() -> new IllegalArgumentException(machine.id() + " has no machine_uses_block edge"));
            ResourceId blockId = ResourceId.parse(graph.requireRuntimeId(blockNodeId));
            LinkedHashMap<ResourceId, Integer> inputs = new LinkedHashMap<>();
            LinkedHashMap<ResourceId, String> inputNodeIds = new LinkedHashMap<>();
            for (GraphEdgeView input : graph.outgoing(machine.id(), "machine_consumes_item")) {
                ResourceId runtimeId = ResourceId.parse(graph.requireRuntimeId(input.target()));
                inputs.merge(runtimeId, Math.max(1, input.integer("count", 1)), Integer::sum);
                inputNodeIds.put(runtimeId, input.target());
            }
            GraphEdgeView outputEdge = graph.outgoing(machine.id(), "machine_outputs_item").stream()
                    .findFirst()
                    .orElse(null);
            String outputNode = outputEdge == null ? "" : outputEdge.target();
            ResourceId output = outputNode.isBlank() ? null : ResourceId.parse(graph.requireRuntimeId(outputNode));
            MachinePlan plan = new MachinePlan(
                    machine.id(),
                    machine.displayName(),
                    Map.copyOf(inputs),
                    Map.copyOf(inputNodeIds),
                    outputNode,
                    output,
                    outputEdge == null ? 0 : Math.max(1, outputEdge.integer("count", 1)),
                    machine.decimal("exposureReduction", 0.0)
            );
            if (result.putIfAbsent(blockId, plan) != null) {
                throw new IllegalArgumentException("Multiple machine nodes target block " + blockNodeId);
            }
        }
        return Map.copyOf(result);
    }

    private void completeObjective(ModuleState state, String completedNode) {
        String current = state.get("objectiveNodeId", objectives.get(0));
        if (!current.equals(completedNode)) return;
        int index = objectives.indexOf(completedNode);
        if (index >= 0 && index + 1 < objectives.size()) state.put("objectiveNodeId", objectives.get(index + 1));
    }

    private String objectiveLabel(String nodeId) {
        return graph.requireNode(nodeId).displayName();
    }

    private boolean stormActive(double timeSeconds) {
        double cycle = Math.max(1.0, tuning.stormCycleSeconds());
        double active = Math.max(0.0, Math.min(cycle, tuning.stormActiveSeconds()));
        return timeSeconds >= cycle && timeSeconds % cycle < active;
    }

    private static int positiveCount(Object value, String label) {
        int result;
        if (value instanceof Number number) result = number.intValue();
        else {
            try {
                result = Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException failure) {
                throw new IllegalArgumentException(label + " count is not an integer", failure);
            }
        }
        if (result <= 0) throw new IllegalArgumentException(label + " count must be positive");
        return result;
    }

    private record MachinePlan(
            String nodeId,
            String displayName,
            Map<ResourceId, Integer> inputs,
            Map<ResourceId, String> inputNodeIds,
            String outputNodeId,
            ResourceId output,
            int outputCount,
            double exposureReduction
    ) {
    }
}
