package dev.echo.engine.game;

import dev.echo.engine.runtime.module.ModuleHost;
import dev.echo.engine.runtime.module.PackManifest;
import dev.echo.engine.util.SimpleJson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class VerifyCommand {
    private VerifyCommand() {}

    public static void run(LaunchOptions options) throws Exception {
        PackManifest manifest = PackManifest.load(options.manifest());
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("schemaVersion", "echo.engine.verify.v1");
        report.put("engineVersion", GameSession.ENGINE_VERSION);
        report.put("generatedAt", Instant.now().toString());
        report.put("packId", manifest.id());
        report.put("packVersion", manifest.version());
        report.put("packRoot", options.packRoot().toString());
        report.put("manifest", options.manifest().toString());

        boolean pass;
        String failureReason = null;
        String failureType = null;
        try (ModuleHost host = new ModuleHost(options.packRoot(), manifest)) {
            host.load();
            pass = true;
            report.put("contentGraph", Map.of(
                    "fingerprint", host.contentMap().fingerprint(),
                    "nodes", host.contentMap().nodes().size(),
                    "edges", host.contentMap().edges().size()
            ));
        } catch (Throwable failure) {
            pass = false;
            failureType = failure.getClass().getSimpleName();
            failureReason = failure.getMessage();
        }

        report.put("status", pass ? "PASS" : "FAIL");
        if (!pass) {
            report.put("failureType", failureType);
            report.put("failureReason", failureReason);
        }

        Path reportPath = options.packRoot().resolve("verify-report.json");
        Files.writeString(reportPath, SimpleJson.stringify(report) + "\n");
        System.out.println(SimpleJson.stringify(report));
        if (!pass) {
            System.exit(1);
        }
    }
}
