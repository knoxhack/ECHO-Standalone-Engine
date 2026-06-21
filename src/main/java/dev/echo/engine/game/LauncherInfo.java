package dev.echo.engine.game;

import dev.echo.engine.runtime.module.PackManifest;
import dev.echo.engine.util.SimpleJson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LauncherInfo {
    private LauncherInfo() {}

    public static void print(LaunchOptions options) throws IOException {
        PackManifest manifest = PackManifest.load(options.manifest());

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("schemaVersion", "echo.engine.launcher_info.v1");
        info.put("engineVersion", GameSession.ENGINE_VERSION);
        info.put("javaVersion", System.getProperty("java.version"));
        info.put("javaVendor", System.getProperty("java.vendor"));
        info.put("packRoot", options.packRoot().toString());
        info.put("manifest", options.manifest().toString());
        info.put("saveRoot", options.saveRoot().toString());
        info.put("packId", manifest.id());
        info.put("packName", manifest.name());
        info.put("packVersion", manifest.version());
        info.put("engineVersionRequired", manifest.engineVersion());
        info.put("runtimeTarget", manifest.runtimeTarget());
        info.put("strictArtifacts", manifest.strictArtifacts());
        info.put("strictContentGraph", manifest.strictContentGraph());
        info.put("requireCrossRuntimeParity", manifest.requireCrossRuntimeParity());
        info.put("moduleCount", manifest.modules().size());
        info.put("supportedModes", List.of("game", "headless-smoke", "verify", "repair", "launcher-info"));
        info.put("contentGraphEvidencePath", evidencePath(options.packRoot(), "content-graph-evidence.json"));
        info.put("checksumsPath", evidencePath(options.packRoot(), "checksums.sha256"));

        System.out.println(SimpleJson.stringify(info));
    }

    private static String evidencePath(Path packRoot, String name) {
        Path path = packRoot.resolve(name);
        return Files.isRegularFile(path) ? path.toString() : null;
    }
}
