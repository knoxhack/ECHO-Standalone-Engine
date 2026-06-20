package dev.echo.engine.runtime.adaptercore;

import dev.echo.engine.api.BlockDefinition;
import dev.echo.engine.api.EntityDefinition;
import dev.echo.engine.api.ItemDefinition;
import dev.echo.engine.api.RecipeDefinition;
import dev.echo.engine.api.ResourceId;
import dev.echo.engine.api.graph.GraphEdgeView;
import dev.echo.engine.api.graph.GraphNodeView;
import dev.echo.engine.runtime.content.CanonicalContentMap;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Converts canonical graph nodes and relationships into standalone runtime definitions. */
final class GraphDefinitionFactory {
    private static final Set<String> BLOCK_FIELDS = Set.of(
            "argb", "color", "solid", "opaque", "hardness", "hazardPerSecond", "emittedLight",
            "drop", "dropNodeId"
    );
    private static final Set<String> ITEM_FIELDS = Set.of(
            "maxStack", "maxStackSize", "placesBlock", "placesBlockNodeId", "foodRestore", "hydrationRestore"
    );
    private static final Set<String> ENTITY_FIELDS = Set.of(
            "argb", "color", "maxHealth", "moveSpeed", "hostile", "spawnWeight"
    );

    private GraphDefinitionFactory() {
    }

    static BlockDefinition block(CanonicalContentMap graph, GraphNodeView node) {
        ResourceId id = runtimeId(graph, node.id());
        LinkedHashMap<String, String> properties = scalarProperties(node.attributes(), BLOCK_FIELDS);
        List<GraphEdgeView> drops = graph.outgoing(node.id(), "block_drops_item");
        String drop = drops.isEmpty()
                ? firstNonBlank(node.string("dropNodeId", ""), node.string("drop", ""))
                : drops.get(0).target();
        if (!drop.isBlank()) properties.put("drop", resolveReference(graph, drop));
        return new BlockDefinition(
                id,
                node.displayName(),
                color(node.attributes().getOrDefault("argb", node.attributes().get("color")), 0xFF888888),
                node.bool("solid", true),
                node.bool("opaque", true),
                node.decimal("hardness", 1.0),
                node.decimal("hazardPerSecond", 0.0),
                node.integer("emittedLight", 0),
                properties
        );
    }

    static ItemDefinition item(CanonicalContentMap graph, GraphNodeView node) {
        ResourceId id = runtimeId(graph, node.id());
        List<GraphEdgeView> placements = graph.outgoing(node.id(), "item_places_block");
        String placement = placements.isEmpty()
                ? firstNonBlank(node.string("placesBlockNodeId", ""), node.string("placesBlock", ""))
                : placements.get(0).target();
        return new ItemDefinition(
                id,
                node.displayName(),
                Math.max(1, node.integer("maxStack", node.integer("maxStackSize", 64))),
                placement.isBlank() ? null : ResourceId.parse(resolveReference(graph, placement)),
                node.decimal("foodRestore", 0.0),
                node.decimal("hydrationRestore", 0.0),
                scalarProperties(node.attributes(), ITEM_FIELDS)
        );
    }

    static RecipeDefinition recipe(CanonicalContentMap graph, GraphNodeView node) {
        LinkedHashMap<ResourceId, Integer> ingredients = new LinkedHashMap<>();
        List<GraphEdgeView> consumes = graph.outgoing(node.id(), "recipe_consumes_item");
        if (!consumes.isEmpty()) {
            for (GraphEdgeView edge : consumes) {
                ingredients.merge(
                        ResourceId.parse(resolveReference(graph, edge.target())),
                        Math.max(1, edge.integer("count", 1)),
                        Integer::sum
                );
            }
        } else {
            Object value = node.attributes().get("ingredients");
            if (value instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    String nodeOrRuntimeId = String.valueOf(entry.getKey());
                    int count = entry.getValue() instanceof Number number
                            ? number.intValue()
                            : Integer.parseInt(String.valueOf(entry.getValue()));
                    ingredients.put(ResourceId.parse(resolveReference(graph, nodeOrRuntimeId)), Math.max(1, count));
                }
            }
        }
        List<GraphEdgeView> outputs = graph.outgoing(node.id(), "recipe_outputs_item");
        String result = outputs.isEmpty()
                ? firstNonBlank(node.string("resultNodeId", ""), node.string("result", ""))
                : outputs.get(0).target();
        int resultCount = outputs.isEmpty()
                ? node.integer("resultCount", 1)
                : outputs.get(0).integer("count", 1);
        if (result.isBlank()) throw new IllegalArgumentException("Recipe node has no result relation: " + node.id());
        if (ingredients.isEmpty()) throw new IllegalArgumentException("Recipe node has no ingredient relations: " + node.id());
        return new RecipeDefinition(
                runtimeId(graph, node.id()),
                ingredients,
                ResourceId.parse(resolveReference(graph, result)),
                Math.max(1, resultCount)
        );
    }

    static EntityDefinition entity(CanonicalContentMap graph, GraphNodeView node) {
        LinkedHashMap<String, String> properties = scalarProperties(node.attributes(), ENTITY_FIELDS);
        graph.outgoing(node.id(), "entity_uses_role").stream().findFirst()
                .ifPresent(edge -> properties.put("role", edge.target()));
        return new EntityDefinition(
                runtimeId(graph, node.id()),
                node.displayName(),
                color(node.attributes().getOrDefault("argb", node.attributes().get("color")), 0xFFFFFFFF),
                node.decimal("maxHealth", 10.0),
                node.decimal("moveSpeed", 2.0),
                node.bool("hostile", false),
                Math.max(0, node.integer("spawnWeight", 1)),
                properties
        );
    }

    static ResourceId runtimeId(CanonicalContentMap graph, String nodeId) {
        return ResourceId.parse(graph.requireBinding(nodeId, CanonicalContentMap.STANDALONE_TARGET).runtimeId());
    }

    static String resolveReference(CanonicalContentMap graph, String nodeOrRuntimeId) {
        if (graph.node(nodeOrRuntimeId).isPresent()) {
            return graph.requireBinding(nodeOrRuntimeId, CanonicalContentMap.STANDALONE_TARGET).runtimeId();
        }
        return nodeOrRuntimeId;
    }

    private static LinkedHashMap<String, String> scalarProperties(Map<String, Object> attributes, Set<String> excluded) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        attributes.forEach((key, value) -> {
            if (!excluded.contains(key) && value != null && !(value instanceof Map<?, ?>) && !(value instanceof Iterable<?>)) {
                result.put(key, String.valueOf(value));
            }
        });
        return result;
    }

    private static int color(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        if (value == null) return fallback;
        String text = String.valueOf(value).trim().replace("#", "");
        if (text.startsWith("0x") || text.startsWith("0X")) text = text.substring(2);
        try {
            long parsed = Long.parseLong(text, 16);
            if (text.length() <= 6) parsed |= 0xFF000000L;
            return (int) parsed;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return "";
    }
}
