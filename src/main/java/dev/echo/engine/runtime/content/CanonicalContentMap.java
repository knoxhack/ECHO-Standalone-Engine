package dev.echo.engine.runtime.content;

import dev.echo.engine.api.graph.ContentGraphView;
import dev.echo.engine.api.graph.GraphEdgeView;
import dev.echo.engine.api.graph.GraphNodeView;
import dev.echo.engine.util.SimpleJson;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Immutable merged canonical map for every installed module. */
public final class CanonicalContentMap {
    public static final String STANDALONE_TARGET = "echo_runtime_standalone";
    public static final List<String> CROSS_RUNTIME_TARGETS = List.of("echo_native", "neoforge", STANDALONE_TARGET);

    private final Map<String, GraphNodeView> nodes;
    private final List<GraphEdgeView> edges;
    private final Map<String, ModuleContentBundle> modules;
    private final Map<String, List<GraphEdgeView>> outgoing;
    private final String fingerprint;

    CanonicalContentMap(
            Map<String, GraphNodeView> nodes,
            List<GraphEdgeView> edges,
            Map<String, ModuleContentBundle> modules
    ) {
        this.nodes = Map.copyOf(new LinkedHashMap<>(nodes));
        this.edges = List.copyOf(edges);
        this.modules = Map.copyOf(new LinkedHashMap<>(modules));
        LinkedHashMap<String, List<GraphEdgeView>> outgoingBuilder = new LinkedHashMap<>();
        for (GraphEdgeView edge : edges) {
            outgoingBuilder.computeIfAbsent(edge.source(), ignored -> new ArrayList<>()).add(edge);
        }
        LinkedHashMap<String, List<GraphEdgeView>> immutableOutgoing = new LinkedHashMap<>();
        outgoingBuilder.forEach((id, values) -> immutableOutgoing.put(id, List.copyOf(values)));
        this.outgoing = Map.copyOf(immutableOutgoing);
        this.fingerprint = computeFingerprint();
    }

    public Map<String, GraphNodeView> nodes() {
        return nodes;
    }

    public List<GraphEdgeView> edges() {
        return edges;
    }

    public Map<String, ModuleContentBundle> modules() {
        return modules;
    }

    public String fingerprint() {
        return fingerprint;
    }

    public Optional<GraphNodeView> node(String nodeId) {
        return Optional.ofNullable(nodes.get(nodeId));
    }

    public GraphNodeView requireNode(String nodeId) {
        GraphNodeView node = nodes.get(nodeId);
        if (node == null) throw new IllegalArgumentException("Unknown canonical graph node: " + nodeId);
        return node;
    }

    public Optional<ExportBinding> binding(String nodeId, String runtimeTarget) {
        GraphNodeView node = nodes.get(nodeId);
        if (node == null) return Optional.empty();
        ModuleContentBundle module = modules.get(node.moduleId());
        if (module == null) return Optional.empty();
        ExportPlan plan = module.exportPlans().get(runtimeTarget);
        return plan == null ? Optional.empty() : plan.binding(nodeId);
    }

    public ExportBinding requireBinding(String nodeId, String runtimeTarget) {
        return binding(nodeId, runtimeTarget).orElseThrow(
                () -> new IllegalArgumentException("No " + runtimeTarget + " export binding for " + nodeId)
        );
    }

    public List<GraphNodeView> nodesByKind(String kind) {
        String normalized = normalizeKind(kind);
        return nodes.values().stream()
                .filter(node -> node.kind().equals(normalized))
                .sorted(Comparator.comparing(GraphNodeView::id))
                .toList();
    }

    public List<GraphEdgeView> outgoing(String nodeId, String edgeType) {
        String normalized = edgeType == null ? "" : edgeType.trim().toLowerCase(java.util.Locale.ROOT);
        return outgoing.getOrDefault(nodeId, List.of()).stream()
                .filter(edge -> normalized.isBlank() || edge.type().equalsIgnoreCase(normalized))
                .toList();
    }

    public ContentGraphView moduleView(String moduleId) {
        if (!modules.containsKey(moduleId)) throw new IllegalArgumentException("Unknown module graph: " + moduleId);
        return new ModuleView(moduleId);
    }

    public CrossRuntimeParityReport parityReport() {
        LinkedHashMap<String, Integer> mappedCounts = new LinkedHashMap<>();
        ArrayList<String> blockers = new ArrayList<>();
        List<GraphNodeView> requiredNodes = nodes.values().stream()
                .filter(CanonicalContentMap::requiresRuntimeMapping)
                .sorted(Comparator.comparing(GraphNodeView::id))
                .toList();
        for (String target : CROSS_RUNTIME_TARGETS) {
            int mapped = 0;
            for (GraphNodeView node : requiredNodes) {
                if (binding(node.id(), target).isPresent()) mapped++;
                else blockers.add(target + " missing mapping for " + node.id());
            }
            mappedCounts.put(target, mapped);
        }
        return new CrossRuntimeParityReport(blockers.isEmpty(), requiredNodes.size(), mappedCounts, blockers);
    }

    public static boolean requiresRuntimeMapping(GraphNodeView node) {
        if (node.bool("runtimeOptional", false)) return false;
        return switch (node.kind()) {
            case "BLOCK", "ITEM", "RECIPE", "ENTITY", "LOOT_TABLE", "WORLD_GENERATOR",
                    "GAMEPLAY_SYSTEM", "SURVIVAL_PROFILE", "HAZARD", "WEATHER_PROFILE", "MACHINE",
                    "MISSION", "OBJECTIVE", "UI_SCREEN", "SERVICE", "CAPABILITY" -> true;
            default -> node.bool("runtimeRequired", false);
        };
    }

    private String computeFingerprint() {
        ArrayList<Object> canonicalNodes = new ArrayList<>();
        nodes.values().stream().sorted(Comparator.comparing(GraphNodeView::id)).forEach(node -> {
            TreeMap<String, Object> row = new TreeMap<>();
            row.put("id", node.id());
            row.put("moduleId", node.moduleId());
            row.put("kind", node.kind());
            row.put("displayName", node.displayName());
            row.put("attributes", canonicalize(node.attributes()));
            canonicalNodes.add(row);
        });

        ArrayList<Object> canonicalEdges = new ArrayList<>();
        edges.stream().sorted(Comparator.comparing(GraphEdgeView::id)).forEach(edge -> {
            TreeMap<String, Object> row = new TreeMap<>();
            row.put("id", edge.id());
            row.put("type", edge.type());
            row.put("source", edge.source());
            row.put("target", edge.target());
            row.put("attributes", canonicalize(edge.attributes()));
            canonicalEdges.add(row);
        });

        ArrayList<Object> canonicalModules = new ArrayList<>();
        modules.values().stream().sorted(Comparator.comparing(ModuleContentBundle::moduleId)).forEach(module -> {
            TreeMap<String, Object> row = new TreeMap<>();
            row.put("moduleId", module.moduleId());
            row.put("schemaVersion", module.schemaVersion());

            ArrayList<Object> features = new ArrayList<>();
            module.features().stream().sorted(Comparator.comparing(ContentFeature::id)).forEach(feature -> {
                TreeMap<String, Object> featureRow = new TreeMap<>();
                featureRow.put("id", feature.id());
                featureRow.put("moduleId", feature.moduleId());
                featureRow.put("name", feature.name());
                featureRow.put("nodeIds", List.copyOf(feature.nodeIds()));
                featureRow.put("attributes", canonicalize(feature.attributes()));
                features.add(featureRow);
            });
            row.put("features", features);

            TreeMap<String, Object> exportPlans = new TreeMap<>();
            module.exportPlans().forEach((target, plan) -> {
                ArrayList<Object> bindings = new ArrayList<>();
                plan.bindings().values().stream()
                        .sorted(Comparator.comparing(ExportBinding::nodeId))
                        .forEach(binding -> {
                            TreeMap<String, Object> bindingRow = new TreeMap<>();
                            bindingRow.put("nodeId", binding.nodeId());
                            bindingRow.put("runtimeId", binding.runtimeId());
                            bindingRow.put("adapter", binding.adapter());
                            bindingRow.put("kind", binding.kind());
                            bindingRow.put("attributes", canonicalize(binding.attributes()));
                            bindings.add(bindingRow);
                        });
                TreeMap<String, Object> planRow = new TreeMap<>();
                planRow.put("schemaVersion", plan.schemaVersion());
                planRow.put("runtimeTarget", plan.runtimeTarget());
                planRow.put("bindings", bindings);
                exportPlans.put(target, planRow);
            });
            row.put("exportPlans", exportPlans);
            canonicalModules.add(row);
        });

        TreeMap<String, Object> payload = new TreeMap<>();
        payload.put("nodes", canonicalNodes);
        payload.put("edges", canonicalEdges);
        payload.put("modules", canonicalModules);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(SimpleJson.stringify(payload).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static Object canonicalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> result = new TreeMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), canonicalize(item)));
            return result;
        }
        if (value instanceof Collection<?> collection) {
            ArrayList<Object> result = new ArrayList<>(collection.size());
            for (Object item : collection) result.add(canonicalize(item));
            return result;
        }
        if (value != null && value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            ArrayList<Object> result = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                result.add(canonicalize(java.lang.reflect.Array.get(value, index)));
            }
            return result;
        }
        return value;
    }

    private static String normalizeKind(String kind) {
        return kind == null ? "" : kind.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private final class ModuleView implements ContentGraphView {
        private final String moduleId;

        private ModuleView(String moduleId) {
            this.moduleId = moduleId;
        }

        @Override
        public String moduleId() {
            return moduleId;
        }

        @Override
        public String fingerprint() {
            return CanonicalContentMap.this.fingerprint();
        }

        @Override
        public int totalModuleCount() {
            return CanonicalContentMap.this.modules.size();
        }

        @Override
        public int totalNodeCount() {
            return CanonicalContentMap.this.nodes.size();
        }

        @Override
        public int totalEdgeCount() {
            return CanonicalContentMap.this.edges.size();
        }

        @Override
        public Optional<GraphNodeView> node(String nodeId) {
            return CanonicalContentMap.this.node(nodeId);
        }

        @Override
        public List<GraphNodeView> nodesByKind(String kind) {
            return CanonicalContentMap.this.nodesByKind(kind).stream()
                    .filter(node -> node.moduleId().equals(moduleId))
                    .toList();
        }

        @Override
        public List<GraphEdgeView> outgoing(String nodeId, String edgeType) {
            return CanonicalContentMap.this.outgoing(nodeId, edgeType);
        }

        @Override
        public Optional<String> runtimeId(String nodeId) {
            return CanonicalContentMap.this.binding(nodeId, STANDALONE_TARGET).map(ExportBinding::runtimeId);
        }
    }
}
