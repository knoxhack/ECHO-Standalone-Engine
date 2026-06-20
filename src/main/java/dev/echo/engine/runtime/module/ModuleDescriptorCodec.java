package dev.echo.engine.runtime.module;

import dev.echo.engine.api.ModuleDependency;
import dev.echo.engine.api.ModuleDescriptor;
import dev.echo.engine.util.SimpleJson;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Parser for current ECHO descriptors plus the compact legacy rewrite descriptor shape. */
final class ModuleDescriptorCodec {
    private ModuleDescriptorCodec() {
    }

    static ModuleDescriptor parse(String text) {
        Map<String, Object> json = SimpleJson.parseObject(text);
        Map<String, Object> access = object(json.get("access"));

        ArrayList<ModuleDependency> dependencies = new ArrayList<>();
        parseDependencies(json.get("dependencies"), dependencies);
        parseDependencies(json.get("requires"), dependencies);
        for (Object value : list(json.get("optional"))) {
            if (value instanceof String id) dependencies.add(new ModuleDependency(id, "*", true));
            else {
                Map<String, Object> row = object(value);
                dependencies.add(new ModuleDependency(
                        firstString(row, "id", "moduleId"),
                        firstNonBlank(string(row, "version"), string(row, "versionRange"), "*"),
                        true
                ));
            }
        }

        Set<String> permissions = new LinkedHashSet<>();
        addStrings(permissions, json.get("permissions"));
        addStrings(permissions, access.get("permissions"));

        Set<String> namespaces = new LinkedHashSet<>();
        addStrings(namespaces, json.get("contentNamespaces"));
        addStrings(namespaces, access.get("contentNamespaces"));

        List<String> classPath = new ArrayList<>();
        addStrings(classPath, access.get("nativeClasspath"));
        addStrings(classPath, json.get("classPath"));

        String schemaVersion = json.get("schemaVersion") == null
                ? "echo.module.descriptor.v1"
                : String.valueOf(json.get("schemaVersion"));
        boolean official = bool(json, "official", false);
        String trust = firstNonBlank(string(json, "trust"), official ? "official" : "data-only");
        String entrypoint = firstNonBlank(
                string(access, "nativeEntrypoint"),
                string(access, "standaloneEntrypoint"),
                string(json, "entrypoint")
        );

        return new ModuleDescriptor(
                schemaVersion,
                firstString(json, "id", "moduleId"),
                string(json, "name"),
                string(json, "version"),
                entrypoint,
                trust,
                bool(json, "standalone", true),
                official,
                deduplicateDependencies(dependencies),
                permissions,
                namespaces,
                classPath
        );
    }

    private static void parseDependencies(Object value, List<ModuleDependency> out) {
        for (Object item : list(value)) {
            if (item instanceof String id) {
                out.add(new ModuleDependency(id, "*", false));
                continue;
            }
            Map<String, Object> row = object(item);
            String id = firstString(row, "id", "moduleId");
            if (id.isBlank()) continue;
            out.add(new ModuleDependency(
                    id,
                    firstNonBlank(string(row, "version"), string(row, "versionRange"), "*"),
                    bool(row, "optional", false)
            ));
        }
    }

    private static List<ModuleDependency> deduplicateDependencies(List<ModuleDependency> values) {
        LinkedHashMap<String, ModuleDependency> result = new LinkedHashMap<>();
        for (ModuleDependency value : values) {
            ModuleDependency previous = result.get(value.id());
            if (previous == null || previous.optional() && !value.optional()) result.put(value.id(), value);
        }
        return List.copyOf(result.values());
    }

    private static void addStrings(java.util.Collection<String> target, Object value) {
        if (value instanceof String text) {
            if (!text.isBlank()) target.add(text.trim());
            return;
        }
        for (Object item : list(value)) if (item != null && !String.valueOf(item).isBlank()) target.add(String.valueOf(item).trim());
    }

    static List<?> list(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    static Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?> map)) return Map.of();
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    static String string(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String firstString(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            String value = string(map, key);
            if (!value.isBlank()) return value;
        }
        return "";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return "";
    }

    static boolean bool(Map<String, Object> map, String key, boolean fallback) {
        Object value = map.get(key);
        if (value instanceof Boolean bool) return bool;
        if (value instanceof String text) return Boolean.parseBoolean(text);
        return fallback;
    }
}
