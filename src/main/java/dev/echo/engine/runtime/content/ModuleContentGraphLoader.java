package dev.echo.engine.runtime.content;

import dev.echo.engine.api.graph.GraphEdgeView;
import dev.echo.engine.api.graph.GraphNodeView;
import dev.echo.engine.util.SimpleJson;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Loads the canonical .ECHO Content Graph tree embedded in an installed module JAR. */
public final class ModuleContentGraphLoader {
    public static final String GRAPH_PATH = ".echo/content-graph/content-graph.json";
    public static final String FEATURES_PATH = ".echo/content-graph/features.json";
    public static final String PROVENANCE_PATH = ".echo/content-graph/provenance.json";
    public static final String UNRESOLVED_PATH = ".echo/content-graph/unresolved-references.json";
    public static final String EXPORT_PREFIX = ".echo/content-graph/export-plans/";

    private static final int MAX_JSON_ENTRY_BYTES = 16 * 1024 * 1024;

    public ModuleContentBundle load(Path modulePath, String expectedModuleId, boolean strict)
            throws IOException {
        try (JarFile jar = new JarFile(modulePath.toFile(), true)) {
            Map<String, Object> graphJson = readRequiredObject(jar, GRAPH_PATH, strict);
            if (graphJson.isEmpty()) {
                throw new GraphValidationException(
                        "Module has no canonical ECHO Content Graph",
                        List.of(expectedModuleId + " missing " + GRAPH_PATH));
            }

            Map<String, Object> graphBody = object(graphJson.get("graph"));
            if (graphBody.isEmpty()) {
                graphBody = graphJson;
            }
            String moduleId = firstNonBlank(
                    firstString(graphBody, "moduleId", "module", "owner"),
                    firstString(graphJson, "moduleId", "module", "owner"),
                    expectedModuleId);
            if (!moduleId.equals(expectedModuleId)) {
                throw new GraphValidationException(
                        "Content Graph module identity mismatch",
                        List.of("descriptor=" + expectedModuleId, "graph=" + moduleId));
            }

            List<GraphNodeView> nodes = parseNodes(moduleId, rows(graphBody.get("nodes"), "id"));
            List<GraphEdgeView> edges = parseEdges(rows(graphBody.get("edges"), "id"));
            List<ContentFeature> features = parseFeatures(moduleId, readJson(jar, FEATURES_PATH));
            Map<String, ExportPlan> plans = readExportPlans(jar, moduleId);
            Map<String, Object> provenance = readObject(jar, PROVENANCE_PATH);
            validateProvenanceIdentity(moduleId, provenance);
            List<String> unresolved = parseUnresolved(readJson(jar, UNRESOLVED_PATH));

            if (strict && nodes.isEmpty()) {
                throw new GraphValidationException(
                        "Strict module graph contains no nodes",
                        List.of(moduleId + " graph node count is zero"));
            }
            if (strict && !unresolved.isEmpty()) {
                throw new GraphValidationException(
                        "Strict module graph has unresolved references", unresolved);
            }
            if (strict && !plans.containsKey(CanonicalContentMap.STANDALONE_TARGET)) {
                throw new GraphValidationException(
                        "Strict module has no standalone export plan",
                        List.of(moduleId + " missing " + EXPORT_PREFIX
                                + CanonicalContentMap.STANDALONE_TARGET + ".json"));
            }

            return new ModuleContentBundle(
                    firstNonBlank(string(graphJson, "schemaVersion"), "echo.content_graph.v1"),
                    moduleId,
                    modulePath,
                    nodes,
                    edges,
                    features,
                    plans,
                    provenance,
                    unresolved);
        }
    }

    private static List<GraphNodeView> parseNodes(String moduleId, List<Map<String, Object>> values) {
        ArrayList<GraphNodeView> result = new ArrayList<>();
        for (Map<String, Object> row : values) {
            String id = firstString(row, "id", "nodeId", "contentId");
            String kind = firstString(row, "kind", "type", "nodeType", "contentKind");
            if (id.isBlank() || kind.isBlank()) {
                throw new GraphValidationException(
                        "Invalid Content Graph node",
                        List.of(moduleId + " node requires id and kind: " + row));
            }
            String owner = firstNonBlank(
                    firstString(row, "moduleId", "module", "owner"), moduleId);
            LinkedHashMap<String, Object> attributes = attributes(
                    row,
                    Set.of(
                            "id", "nodeId", "contentId", "kind", "type", "nodeType",
                            "contentKind", "moduleId", "module", "owner", "displayName",
                            "name", "label", "attributes", "properties", "data", "metadata"));
            merge(attributes, row.get("attributes"));
            merge(attributes, row.get("properties"));
            merge(attributes, row.get("data"));
            merge(attributes, row.get("metadata"));
            result.add(new GraphNodeView(
                    id,
                    owner,
                    kind,
                    firstNonBlank(firstString(row, "displayName", "name", "label"), id),
                    attributes));
        }
        return List.copyOf(result);
    }

    private static List<GraphEdgeView> parseEdges(List<Map<String, Object>> values) {
        ArrayList<GraphEdgeView> result = new ArrayList<>();
        int index = 0;
        for (Map<String, Object> row : values) {
            String source = firstString(row, "source", "from", "sourceId");
            String target = firstString(row, "target", "to", "targetId");
            String type = firstString(row, "type", "kind", "relationship", "relation", "edgeType");
            if (source.isBlank() || target.isBlank() || type.isBlank()) {
                throw new GraphValidationException(
                        "Invalid Content Graph edge", List.of(String.valueOf(row)));
            }
            LinkedHashMap<String, Object> attributes = attributes(
                    row,
                    Set.of(
                            "id", "edgeId", "source", "from", "sourceId", "target", "to",
                            "targetId", "type", "kind", "relationship", "relation", "edgeType",
                            "attributes", "properties", "data", "metadata"));
            merge(attributes, row.get("attributes"));
            merge(attributes, row.get("properties"));
            merge(attributes, row.get("data"));
            merge(attributes, row.get("metadata"));
            result.add(new GraphEdgeView(
                    firstNonBlank(firstString(row, "id", "edgeId"), "edge-" + index++),
                    type,
                    source,
                    target,
                    attributes));
        }
        return List.copyOf(result);
    }

    private static List<ContentFeature> parseFeatures(String moduleId, Object value) {
        Map<String, Object> json = object(value);
        Object featureValue = json.isEmpty() ? value : firstPresent(json, "features", "items", "entries");
        ArrayList<ContentFeature> result = new ArrayList<>();
        for (Map<String, Object> row : rows(featureValue, "id")) {
            ArrayList<String> nodeIds = new ArrayList<>();
            for (Object nodeId : list(firstPresent(row, "nodeIds", "nodes", "contentIds"))) {
                if (nodeId != null) {
                    nodeIds.add(String.valueOf(nodeId));
                }
            }
            result.add(new ContentFeature(
                    firstString(row, "id", "featureId"),
                    firstNonBlank(firstString(row, "moduleId", "module"), moduleId),
                    firstString(row, "name", "displayName", "label"),
                    nodeIds,
                    attributes(
                            row,
                            Set.of(
                                    "id", "featureId", "moduleId", "module", "name",
                                    "displayName", "label", "nodeIds", "nodes", "contentIds"))));
        }
        return List.copyOf(result);
    }

    private static Map<String, ExportPlan> readExportPlans(JarFile jar, String moduleId)
            throws IOException {
        LinkedHashMap<String, ExportPlan> plans = new LinkedHashMap<>();
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (entry.isDirectory()
                    || !entry.getName().startsWith(EXPORT_PREFIX)
                    || !entry.getName().endsWith(".json")) {
                continue;
            }
            Map<String, Object> json = readObject(jar, entry);
            String target = normalizeTarget(firstNonBlank(
                    firstString(json, "runtimeTarget", "runtime", "target"),
                    entry.getName().substring(
                            EXPORT_PREFIX.length(), entry.getName().length() - ".json".length())));
            String planModuleId = firstNonBlank(
                    firstString(json, "moduleId", "module"), moduleId);
            if (!planModuleId.equals(moduleId)) {
                throw new GraphValidationException(
                        "Export plan module identity mismatch",
                        List.of("graph=" + moduleId, "plan=" + planModuleId, "target=" + target));
            }

            Object mappingValue = firstPresent(
                    json, "mappings", "exports", "nodes", "entries", "bindings");
            LinkedHashMap<String, ExportBinding> bindings = new LinkedHashMap<>();
            for (Map<String, Object> rawRow : rows(mappingValue, "nodeId")) {
                LinkedHashMap<String, Object> row = new LinkedHashMap<>(rawRow);
                merge(row, row.get("mapping"));
                merge(row, row.get("output"));
                String nodeId = firstString(row, "nodeId", "contentId", "sourceId", "id");
                String runtimeId = firstString(row, "runtimeId", "targetId", "mappedId", "runtimeName");
                if (runtimeId.isBlank() && !nodeId.isBlank() && row.containsKey("id")) {
                    String idValue = string(row, "id");
                    if (!idValue.equals(nodeId)) {
                        runtimeId = idValue;
                    }
                }
                if (nodeId.isBlank() || runtimeId.isBlank()) {
                    throw new GraphValidationException(
                            "Invalid export binding",
                            List.of(moduleId + " target=" + target + " row=" + row));
                }
                ExportBinding binding = new ExportBinding(
                        nodeId,
                        runtimeId,
                        firstString(row, "adapter", "surface", "domain"),
                        firstString(row, "kind", "contentKind", "type"),
                        attributes(
                                row,
                                Set.of(
                                        "nodeId", "contentId", "sourceId", "id", "runtimeId",
                                        "targetId", "mappedId", "runtimeName", "adapter", "surface",
                                        "domain", "kind", "contentKind", "type", "mapping", "output")));
                if (bindings.putIfAbsent(nodeId, binding) != null) {
                    throw new GraphValidationException(
                            "Duplicate export binding",
                            List.of(moduleId + " target=" + target + " node=" + nodeId));
                }
            }
            ExportPlan previous = plans.putIfAbsent(
                    target,
                    new ExportPlan(
                            firstNonBlank(
                                    string(json, "schemaVersion"),
                                    "echo.content_graph.export_plan.v1"),
                            moduleId,
                            target,
                            bindings));
            if (previous != null) {
                throw new GraphValidationException(
                        "Duplicate export target plan",
                        List.of(moduleId + " target=" + target));
            }
        }
        return Map.copyOf(plans);
    }

    private static void validateProvenanceIdentity(
            String moduleId, Map<String, Object> provenance) {
        if (provenance.isEmpty()) {
            return;
        }
        String provenanceModuleId = firstString(provenance, "moduleId", "module", "owner");
        if (!provenanceModuleId.isBlank() && !provenanceModuleId.equals(moduleId)) {
            throw new GraphValidationException(
                    "Content Graph provenance identity mismatch",
                    List.of("graph=" + moduleId, "provenance=" + provenanceModuleId));
        }
    }

    private static List<String> parseUnresolved(Object value) {
        if (value == null) {
            return List.of();
        }
        Object rowsValue = value;
        Map<String, Object> json = object(value);
        if (!json.isEmpty()) {
            rowsValue = firstPresent(json, "unresolvedReferences", "items", "entries");
        }
        ArrayList<String> result = new ArrayList<>();
        if (rowsValue instanceof List<?> list) {
            for (Object item : list) {
                String text = unresolvedText(item);
                if (!text.isBlank()) {
                    result.add(text);
                }
            }
        } else if (rowsValue instanceof Map<?, ?> map) {
            map.forEach((key, item) -> {
                String text = unresolvedText(item);
                result.add(String.valueOf(key) + (text.isBlank() ? "" : ": " + text));
            });
        }
        return List.copyOf(result);
    }

    private static String unresolvedText(Object value) {
        if (!(value instanceof Map<?, ?>)) {
            return value == null ? "" : String.valueOf(value).trim();
        }
        Map<String, Object> row = object(value);
        return firstNonBlank(
                firstString(row, "message", "detail", "reference", "id"),
                String.valueOf(row));
    }

    private static Map<String, Object> readRequiredObject(
            JarFile jar, String name, boolean strict) throws IOException {
        Map<String, Object> value = readObject(jar, name);
        if (strict && value.isEmpty()) {
            throw new GraphValidationException(
                    "Required module graph file is missing", List.of(name));
        }
        return value;
    }

    private static Map<String, Object> readObject(JarFile jar, String name) throws IOException {
        JarEntry entry = jar.getJarEntry(name);
        return entry == null ? Map.of() : readObject(jar, entry);
    }

    private static Map<String, Object> readObject(JarFile jar, JarEntry entry)
            throws IOException {
        return object(readJson(jar, entry));
    }

    private static Object readJson(JarFile jar, String name) throws IOException {
        JarEntry entry = jar.getJarEntry(name);
        return entry == null ? null : readJson(jar, entry);
    }

    private static Object readJson(JarFile jar, JarEntry entry) throws IOException {
        if (entry.getSize() > MAX_JSON_ENTRY_BYTES) {
            throw new GraphValidationException(
                    "Content Graph JSON entry is too large",
                    List.of(entry.getName() + " bytes=" + entry.getSize()));
        }
        try (InputStream input = jar.getInputStream(entry)) {
            byte[] bytes = readBounded(input, MAX_JSON_ENTRY_BYTES);
            return SimpleJson.parse(new String(bytes, StandardCharsets.UTF_8));
        }
    }

    private static byte[] readBounded(InputStream input, int maximumBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maximumBytes, 8192));
        byte[] buffer = new byte[8192];
        int total = 0;
        int count;
        while ((count = input.read(buffer)) >= 0) {
            total += count;
            if (total > maximumBytes) {
                throw new GraphValidationException(
                        "Content Graph JSON entry exceeds the safety limit",
                        List.of("limitBytes=" + maximumBytes));
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static LinkedHashMap<String, Object> attributes(
            Map<String, Object> row, Set<String> ignored) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        row.forEach((key, value) -> {
            if (!ignored.contains(key)) {
                result.put(key, value);
            }
        });
        return result;
    }

    private static void merge(Map<String, Object> target, Object value) {
        object(value).forEach(target::putIfAbsent);
    }

    private static Object firstPresent(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key) && map.get(key) != null) {
                return map.get(key);
            }
        }
        return List.of();
    }

    private static List<Map<String, Object>> rows(Object value, String injectedIdKey) {
        ArrayList<Map<String, Object>> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                Map<String, Object> row = object(item);
                if (!row.isEmpty()) {
                    result.add(row);
                }
            }
        } else if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                LinkedHashMap<String, Object> row = new LinkedHashMap<>(object(entry.getValue()));
                if (row.isEmpty()) {
                    continue;
                }
                row.putIfAbsent(injectedIdKey, String.valueOf(entry.getKey()));
                result.add(row);
            }
        }
        return List.copyOf(result);
    }

    private static List<?> list(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private static Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private static String string(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String firstString(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            String value = string(map, key);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String normalizeTarget(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
