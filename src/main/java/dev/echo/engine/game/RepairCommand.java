package dev.echo.engine.game;

import dev.echo.engine.runtime.module.PackManifest;
import dev.echo.engine.runtime.module.PackModule;
import dev.echo.engine.util.Hashing;
import dev.echo.engine.util.SimpleJson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RepairCommand {
    private RepairCommand() {}

    public static void run(LaunchOptions options) throws IOException {
        PackManifest manifest = PackManifest.load(options.manifest());
        List<Map<String, Object>> failed = new ArrayList<>();
        List<Map<String, Object>> checked = new ArrayList<>();

        for (PackModule module : manifest.modules()) {
            if (!module.required()) {
                continue;
            }
            Path modulePath = options.packRoot().resolve(module.file()).normalize();
            String status;
            String reason = null;
            if (!modulePath.startsWith(options.packRoot())) {
                status = "BLOCKED";
                reason = "Module path escapes pack root";
            } else if (!Files.isRegularFile(modulePath)) {
                status = "MISSING";
                reason = "Required module file is missing";
            } else {
                long actualSize = Files.size(modulePath);
                String actualHash = Hashing.sha256(modulePath);
                if (module.size() > 0L && module.size() != actualSize) {
                    status = "SIZE_MISMATCH";
                    reason = "Expected size " + module.size() + " got " + actualSize;
                } else if (!module.sha256().isBlank() && !actualHash.equalsIgnoreCase(module.sha256())) {
                    status = "HASH_MISMATCH";
                    reason = "SHA-256 mismatch";
                } else {
                    status = "OK";
                }
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("moduleId", module.id());
            row.put("path", module.file());
            row.put("status", status);
            if (reason != null) {
                row.put("reason", reason);
            }
            checked.add(row);

            if (!"OK".equals(status)) {
                Map<String, Object> repair = new LinkedHashMap<>();
                repair.put("moduleId", module.id());
                repair.put("path", module.file());
                repair.put("expectedSha256", module.sha256());
                repair.put("expectedSize", module.size());
                repair.put("status", status);
                repair.put("reason", reason);
                failed.add(repair);
            }
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("schemaVersion", "echo.engine.repair.v1");
        report.put("engineVersion", GameSession.ENGINE_VERSION);
        report.put("generatedAt", Instant.now().toString());
        report.put("packId", manifest.id());
        report.put("packVersion", manifest.version());
        report.put("packRoot", options.packRoot().toString());
        report.put("status", failed.isEmpty() ? "NO_REPAIR_NEEDED" : "REPAIR_REQUIRED");
        report.put("checkedModules", checked);
        report.put("missingOrCorruptModules", failed);

        Path reportPath = options.packRoot().resolve("repair-manifest.json");
        Files.writeString(reportPath, SimpleJson.stringify(report) + "\n");
        System.out.println(SimpleJson.stringify(report));
        if (!failed.isEmpty()) {
            System.exit(2);
        }
    }
}
