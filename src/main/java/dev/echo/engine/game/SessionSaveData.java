package dev.echo.engine.game;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record SessionSaveData(
        int schemaVersion,
        String engineVersion,
        String packId,
        String packVersion,
        String contentGraphFingerprint,
        Map<String, String> moduleFingerprints,
        long seed,
        double timeSeconds,
        double x,
        double y,
        double z,
        double yaw,
        double pitch,
        double health,
        double hunger,
        double hydration,
        double exposure,
        int selectedSlot,
        List<?> inventory,
        Map<String, Map<String, String>> moduleStates
) {
    public SessionSaveData {
        contentGraphFingerprint = contentGraphFingerprint == null ? "" : contentGraphFingerprint.trim();
        moduleFingerprints = moduleFingerprints == null ? Map.of() : Map.copyOf(moduleFingerprints);
        inventory = inventory == null ? List.of() : List.copyOf(inventory);
        moduleStates = moduleStates == null ? Map.of() : Map.copyOf(moduleStates);
    }

    public Map<String, Object> toJson() {
        LinkedHashMap<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", schemaVersion);
        root.put("engineVersion", engineVersion);
        root.put("pack", Map.of("id", packId, "version", packVersion));
        root.put("contentIdentity", Map.of(
                "contentGraphFingerprint", contentGraphFingerprint,
                "moduleFingerprints", moduleFingerprints
        ));
        root.put("seed", seed);
        root.put("timeSeconds", timeSeconds);
        LinkedHashMap<String, Object> player = new LinkedHashMap<>();
        player.put("x", x);
        player.put("y", y);
        player.put("z", z);
        player.put("yaw", yaw);
        player.put("pitch", pitch);
        player.put("health", health);
        player.put("hunger", hunger);
        player.put("hydration", hydration);
        player.put("exposure", exposure);
        player.put("selectedSlot", selectedSlot);
        player.put("inventory", inventory);
        root.put("player", player);
        root.put("moduleStates", moduleStates);
        return root;
    }

    public static SessionSaveData fromJson(Map<String, Object> root) {
        Map<String, Object> pack = object(root.get("pack"));
        Map<String, Object> identity = object(root.get("contentIdentity"));
        Map<String, Object> player = object(root.get("player"));
        LinkedHashMap<String, String> fingerprints = new LinkedHashMap<>();
        object(identity.get("moduleFingerprints")).forEach((id, value) -> fingerprints.put(id, String.valueOf(value)));
        LinkedHashMap<String, Map<String, String>> states = new LinkedHashMap<>();
        object(root.get("moduleStates")).forEach((id, value) -> {
            LinkedHashMap<String, String> row = new LinkedHashMap<>();
            object(value).forEach((key, item) -> row.put(key, String.valueOf(item)));
            states.put(id, Map.copyOf(row));
        });
        return new SessionSaveData(
                integer(root, "schemaVersion", 1),
                string(root, "engineVersion"),
                string(pack, "id"),
                string(pack, "version"),
                string(identity, "contentGraphFingerprint"),
                Map.copyOf(fingerprints),
                longValue(root, "seed", 0L),
                number(root, "timeSeconds", 0.0),
                number(player, "x", 0.0),
                number(player, "y", 40.0),
                number(player, "z", 0.0),
                number(player, "yaw", 0.0),
                number(player, "pitch", 0.0),
                number(player, "health", 20.0),
                number(player, "hunger", 20.0),
                number(player, "hydration", 20.0),
                number(player, "exposure", 0.0),
                integer(player, "selectedSlot", 0),
                list(player.get("inventory")),
                Map.copyOf(states)
        );
    }

    private static Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?> raw)) return Map.of();
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private static List<?> list(Object value) {
        return value instanceof List<?> list ? List.copyOf(list) : List.of();
    }

    private static String string(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static int integer(Map<String, Object> map, String key, int fallback) {
        Object value = map.get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static long longValue(Map<String, Object> map, String key, long fallback) {
        Object value = map.get(key);
        return value instanceof Number number ? number.longValue() : fallback;
    }

    private static double number(Map<String, Object> map, String key, double fallback) {
        Object value = map.get(key);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }
}
