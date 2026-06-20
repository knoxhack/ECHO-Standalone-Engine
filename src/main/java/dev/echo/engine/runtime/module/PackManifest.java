package dev.echo.engine.runtime.module;

import dev.echo.engine.util.SimpleJson;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Normalized ECHO pack manifest. It accepts both compact manifests and edition manifests that expose
 * {@code moduleRequirements} plus {@code files} rows.
 */
public record PackManifest(
        String schemaVersion,
        String id,
        String name,
        String version,
        String engineVersion,
        String runtimeTarget,
        boolean strictArtifacts,
        boolean strictContentGraph,
        boolean requireCrossRuntimeParity,
        List<PackModule> modules
) {
    public PackManifest {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank()
                ? "echo.pack.v1"
                : schemaVersion.trim();
        id = normalizeId(id);
        name = name == null || name.isBlank() ? id : name.trim();
        version = version == null || version.isBlank() ? "0.0.0" : version.trim();
        engineVersion = engineVersion == null || engineVersion.isBlank()
                ? "*"
                : engineVersion.trim();
        runtimeTarget = runtimeTarget == null || runtimeTarget.isBlank()
                ? "echo_runtime_standalone"
                : runtimeTarget.trim().toLowerCase(java.util.Locale.ROOT).replace('-', '_');
        modules = modules == null ? List.of() : List.copyOf(modules);
        if (id.isBlank()) {
            throw new IllegalArgumentException("pack id required");
        }
        if (!runtimeTarget.equals("echo_runtime_standalone")) {
            throw new IllegalArgumentException("Unsupported runtime target: " + runtimeTarget);
        }
        if (modules.isEmpty()) {
            throw new IllegalArgumentException("pack must declare module requirements");
        }
        Set<String> ids = new LinkedHashSet<>();
        for (PackModule module : modules) {
            if (!ids.add(module.id())) {
                throw new IllegalArgumentException("duplicate pack module requirement: " + module.id());
            }
            if (strictArtifacts && module.required()) {
                if (module.sha256().isBlank()) {
                    throw new IllegalArgumentException(
                            "strict pack requires SHA-256 for " + module.id());
                }
                if (module.size() <= 0L) {
                    throw new IllegalArgumentException(
                            "strict pack requires a positive artifact size for " + module.id());
                }
            }
        }
    }

    public static PackManifest load(Path path) throws IOException {
        Map<String, Object> json = SimpleJson.readObject(path);
        Map<String, Map<String, Object>> filesByModule = indexFiles(list(json.get("files")));
        List<?> requirementRows = list(json.get("moduleRequirements"));
        if (requirementRows.isEmpty()) {
            requirementRows = list(json.get("modules"));
        }

        ArrayList<PackModule> modules = new ArrayList<>();
        for (Object value : requirementRows) {
            Map<String, Object> row = value instanceof String
                    ? Map.of("moduleId", String.valueOf(value))
                    : object(value);
            String id = firstString(row, "moduleId", "id");
            Map<String, Object> fileRow = filesByModule.getOrDefault(id, Map.of());
            String file = firstNonBlank(
                    firstString(row, "path", "file"),
                    firstString(fileRow, "path", "file"));
            String sha256 = firstNonBlank(string(row, "sha256"), string(fileRow, "sha256"));
            long size = Math.max(
                    longNumber(row, "size", 0L),
                    longNumber(fileRow, "size", 0L));
            String family = firstNonBlank(
                    string(row, "artifactFamily"),
                    string(fileRow, "artifactFamily"));
            modules.add(new PackModule(
                    id,
                    firstNonBlank(string(row, "version"), string(fileRow, "version"), "*"),
                    file,
                    sha256,
                    size,
                    bool(row, "required", bool(fileRow, "required", true)),
                    firstNonBlank(string(row, "trust"), string(fileRow, "trust"), "official"),
                    firstNonBlank(family, "standalone")));
        }

        Object schemaValue = json.get("schemaVersion");
        String schemaVersion = schemaValue == null
                ? "echo.pack.v1"
                : String.valueOf(schemaValue);
        return new PackManifest(
                schemaVersion,
                firstString(json, "id", "pack"),
                string(json, "name"),
                string(json, "version"),
                firstNonBlank(string(json, "engineVersion"), "*"),
                firstNonBlank(string(json, "runtimeTarget"), "echo_runtime_standalone"),
                bool(json, "strictArtifacts", true),
                bool(json, "strictContentGraph", true),
                bool(json, "requireCrossRuntimeParity", true),
                modules);
    }

    private static Map<String, Map<String, Object>> indexFiles(List<?> values) {
        LinkedHashMap<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Object value : values) {
            Map<String, Object> row = object(value);
            String id = firstString(row, "moduleId", "id");
            if (!id.isBlank() && result.putIfAbsent(id, row) != null) {
                throw new IllegalArgumentException("duplicate files row for module " + id);
            }
        }
        return result;
    }

    static List<?> list(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    static Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
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

    static boolean bool(Map<String, Object> map, String key, boolean fallback) {
        Object value = map.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text);
        }
        return fallback;
    }

    private static long longNumber(Map<String, Object> map, String key, long fallback) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static String normalizeId(String value) {
        return value == null
                ? ""
                : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
