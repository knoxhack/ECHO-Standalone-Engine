package dev.echo.engine.runtime.content;

import dev.echo.engine.api.ResourceId;
import dev.echo.engine.api.graph.GraphEdgeView;
import dev.echo.engine.api.graph.GraphNodeView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Validates and merges installed module graphs into one canonical content map. */
public final class ContentGraphMerger {
    public CanonicalContentMap merge(
            List<ModuleContentBundle> bundles, boolean requireCrossRuntimeParity) {
        LinkedHashMap<String, ModuleContentBundle> modules = new LinkedHashMap<>();
        LinkedHashMap<String, GraphNodeView> nodes = new LinkedHashMap<>();
        ArrayList<GraphEdgeView> edges = new ArrayList<>();
        ArrayList<String> diagnostics = new ArrayList<>();
        Set<String> edgeIds = new LinkedHashSet<>();

        for (ModuleContentBundle bundle : bundles) {
            if (modules.putIfAbsent(bundle.moduleId(), bundle) != null) {
                diagnostics.add("duplicate module graph " + bundle.moduleId());
                continue;
            }
            if (!bundle.unresolvedReferences().isEmpty()) {
                diagnostics.addAll(bundle.unresolvedReferences().stream()
                        .map(value -> bundle.moduleId() + " unresolved: " + value)
                        .toList());
            }
            for (GraphNodeView node : bundle.nodes()) {
                if (!node.moduleId().equals(bundle.moduleId())) {
                    diagnostics.add(bundle.moduleId() + " contains node owned by "
                            + node.moduleId() + ": " + node.id());
                }
                GraphNodeView previous = nodes.putIfAbsent(node.id(), node);
                if (previous != null) {
                    diagnostics.add("duplicate canonical node " + node.id());
                }
            }
            for (GraphEdgeView edge : bundle.edges()) {
                if (!edgeIds.add(edge.id())) {
                    diagnostics.add("duplicate canonical edge id " + edge.id());
                }
                edges.add(edge);
            }
        }

        for (GraphEdgeView edge : edges) {
            if (!nodes.containsKey(edge.source())) {
                diagnostics.add("edge " + edge.id() + " missing source " + edge.source());
            }
            if (!nodes.containsKey(edge.target())) {
                diagnostics.add("edge " + edge.id() + " missing target " + edge.target());
            }
        }

        LinkedHashMap<String, Set<String>> runtimeIdentityKeysByTarget = new LinkedHashMap<>();
        for (String target : CanonicalContentMap.CROSS_RUNTIME_TARGETS) {
            runtimeIdentityKeysByTarget.put(target, new LinkedHashSet<>());
        }

        for (ModuleContentBundle bundle : bundles) {
            if (requireCrossRuntimeParity) {
                for (String target : CanonicalContentMap.CROSS_RUNTIME_TARGETS) {
                    if (!bundle.exportPlans().containsKey(target)) {
                        diagnostics.add(bundle.moduleId() + " missing required export plan " + target);
                    }
                }
            }
            for (Map.Entry<String, ExportPlan> planEntry : bundle.exportPlans().entrySet()) {
                String target = planEntry.getKey();
                ExportPlan plan = planEntry.getValue();
                if (!target.equals(plan.runtimeTarget())) {
                    diagnostics.add(bundle.moduleId() + " export target key mismatch: key="
                            + target + " plan=" + plan.runtimeTarget());
                }
                Set<String> targetIdentities = runtimeIdentityKeysByTarget.computeIfAbsent(
                        target, ignored -> new LinkedHashSet<>());
                for (ExportBinding binding : plan.bindings().values()) {
                    GraphNodeView node = nodes.get(binding.nodeId());
                    if (node == null) {
                        diagnostics.add(bundle.moduleId() + " " + target
                                + " plan references unknown node " + binding.nodeId());
                        continue;
                    }
                    if (!node.moduleId().equals(bundle.moduleId())) {
                        diagnostics.add(bundle.moduleId() + " exports node owned by "
                                + node.moduleId() + ": " + node.id());
                    }
                    if (!binding.kind().isBlank()
                            && !normalizeKind(binding.kind()).equals(node.kind())) {
                        diagnostics.add(bundle.moduleId() + " " + target + " binding kind "
                                + binding.kind() + " does not match " + node.kind()
                                + " for " + node.id());
                    }
                    if (CanonicalContentMap.requiresRuntimeMapping(node)
                            && binding.adapter().isBlank()) {
                        diagnostics.add(bundle.moduleId() + " " + target
                                + " binding has no AdapterCore/export surface for " + node.id());
                    }
                    String identity = node.kind() + "\n" + binding.runtimeId();
                    if (!targetIdentities.add(identity)) {
                        diagnostics.add("duplicate " + target + " runtime identity "
                                + binding.runtimeId() + " for kind " + node.kind());
                    }
                    if (target.equals(CanonicalContentMap.STANDALONE_TARGET)
                            && requiresResourceId(node.kind())) {
                        try {
                            ResourceId.parse(binding.runtimeId());
                        } catch (IllegalArgumentException failure) {
                            diagnostics.add("invalid standalone runtime id " + binding.runtimeId()
                                    + " for " + node.id());
                        }
                    }
                }
            }
            for (ContentFeature feature : bundle.features()) {
                if (!feature.moduleId().equals(bundle.moduleId())) {
                    diagnostics.add(bundle.moduleId() + " contains feature owned by "
                            + feature.moduleId() + ": " + feature.id());
                }
                for (String nodeId : feature.nodeIds()) {
                    if (!nodes.containsKey(nodeId)) {
                        diagnostics.add(bundle.moduleId() + " feature " + feature.id()
                                + " references unknown node " + nodeId);
                    }
                }
            }
        }

        CanonicalContentMap result = new CanonicalContentMap(nodes, edges, modules);
        for (GraphNodeView node : nodes.values()) {
            if (CanonicalContentMap.requiresRuntimeMapping(node)
                    && result.binding(node.id(), CanonicalContentMap.STANDALONE_TARGET).isEmpty()) {
                diagnostics.add("standalone export plan missing required node " + node.id());
            }
        }

        if (requireCrossRuntimeParity) {
            diagnostics.addAll(result.parityReport().blockers());
        }
        if (!diagnostics.isEmpty()) {
            diagnostics.sort(Comparator.naturalOrder());
            throw new GraphValidationException(
                    "Canonical Content Graph validation failed", diagnostics);
        }
        return result;
    }

    private static boolean requiresResourceId(String kind) {
        return switch (kind) {
            case "BLOCK", "ITEM", "RECIPE", "ENTITY", "WORLD_GENERATOR" -> true;
            default -> false;
        };
    }

    private static String normalizeKind(String kind) {
        return kind == null ? "" : kind.trim().toUpperCase(Locale.ROOT);
    }
}
