package dev.echo.engine.game;

import dev.echo.engine.api.GameAudio;
import dev.echo.engine.api.GameExtension;
import dev.echo.engine.api.adapter.AdapterCoreDescriptorService;
import dev.echo.engine.runtime.adaptercore.AdapterCoreAudit;
import dev.echo.engine.runtime.content.CrossRuntimeParityReport;
import dev.echo.engine.runtime.module.ModuleHost;
import dev.echo.engine.runtime.module.PackManifest;
import dev.echo.engine.util.SimpleJson;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;

public final class HeadlessSmoke {
    private HeadlessSmoke() {
    }

    public static void run(LaunchOptions options) throws Exception {
        Path smokeRoot = options.saveRoot().resolve("headless-smoke");
        delete(smokeRoot);
        PackManifest pack = PackManifest.load(options.manifest());
        try (ModuleHost modules = new ModuleHost(options.packRoot(), pack)) {
            modules.load();
            verifyCanonicalBoot(modules);
            GameAudio audio = cue -> { };
            GameSession first = GameSession.createNew("smoke-world", smokeRoot, 424242L, modules, audio);
            awaitReady(first);
            for (int index = 0; index < 600; index++) first.updateHeadless(1.0 / 60.0);
            first.save(true);

            int blockCount = modules.registries().runtimeBlocks().size();
            int itemCount = modules.registries().runtimeItems().size();
            int recipeCount = modules.registries().runtimeRecipes().size();
            int entityDefinitionCount = modules.registries().runtimeEntities().size();
            int moduleCount = modules.loadedModules().size();
            int chunkCount = first.voxelWorld().chunks().size();
            int entityCount = first.entityCount();
            String graphFingerprint = first.contentGraphFingerprint();
            first.close();

            GameSession restored = GameSession.load("smoke-world", smokeRoot, modules, audio);
            awaitReady(restored);
            if (restored.voxelWorld().chunks().isEmpty()) throw new AssertionError("restored world has no chunks");
            if (!restored.contentGraphFingerprint().equals(graphFingerprint)) {
                throw new AssertionError("restored session graph identity changed");
            }
            if (blockCount < 8 || itemCount < 8 || entityDefinitionCount < 1 || moduleCount < 10) {
                throw new AssertionError("canonical Ashfall pack content is incomplete");
            }
            restored.close();

            AdapterCoreAudit adapter = modules.adapterCore().audit();
            CrossRuntimeParityReport parity = modules.contentMap().parityReport();
            LinkedHashMap<String, Object> report = new LinkedHashMap<>();
            report.put("schema", "echo.engine.headless_smoke.v2");
            report.put("status", "PASS");
            report.put("engineVersion", GameSession.ENGINE_VERSION);
            report.put("packId", pack.id());
            report.put("installedModules", moduleCount);
            report.put("contentGraph", java.util.Map.of(
                    "canonical", true,
                    "fingerprint", graphFingerprint,
                    "modules", modules.contentMap().modules().size(),
                    "nodes", modules.contentMap().nodes().size(),
                    "edges", modules.contentMap().edges().size(),
                    "crossRuntimeParity", parity.ready(),
                    "mappedNodeCounts", parity.mappedNodeCounts()
            ));
            report.put("adapterCore", java.util.Map.of(
                    "canonicalRuntimeBridge", true,
                    "ready", adapter.ready(),
                    "accepted", adapter.accepted(),
                    "rejected", adapter.rejected(),
                    "revoked", adapter.revoked(),
                    "acceptedByDomain", adapter.acceptedByDomain()
            ));
            report.put("runtimeContent", java.util.Map.of(
                    "blocks", blockCount,
                    "items", itemCount,
                    "recipes", recipeCount,
                    "entityDefinitions", entityDefinitionCount,
                    "gameExtensions", modules.services().all(GameExtension.class).size()
            ));
            report.put("world", java.util.Map.of(
                    "chunks", chunkCount,
                    "spawnedEntities", entityCount,
                    "saveReload", true,
                    "contentIdentityVerified", true
            ));
            SimpleJson.write(smokeRoot.resolve("headless-smoke-report.json"), report);
            System.out.println("HEADLESS SMOKE PASS " + report);
        }
    }

    private static void verifyCanonicalBoot(ModuleHost modules) {
        if (!modules.contentMap().parityReport().ready()) {
            throw new AssertionError("cross-runtime export parity failed: " + modules.contentMap().parityReport().blockers());
        }
        if (!modules.adapterCore().audit().ready()) {
            throw new AssertionError("AdapterCore mutation audit failed");
        }
        AdapterCoreDescriptorService descriptor = modules.services().first(AdapterCoreDescriptorService.class)
                .orElseThrow(() -> new AssertionError("installed AdapterCore module did not publish its descriptor service"));
        if (!descriptor.graphFingerprint().equals(modules.contentMap().fingerprint())) {
            throw new AssertionError("AdapterCore service graph identity mismatch");
        }
        if (modules.services().all(GameExtension.class).isEmpty()) {
            throw new AssertionError("Ashfall module did not bind a graph-backed gameplay extension");
        }
    }

    private static void awaitReady(GameSession session) throws Exception {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (!session.ready() && System.nanoTime() < deadline) {
            session.updateHeadless(1.0 / 60.0);
            Thread.sleep(5L);
        }
        if (!session.ready()) throw new AssertionError("world did not become ready");
    }

    private static void delete(Path root) throws Exception {
        if (!Files.exists(root)) return;
        try (var stream = Files.walk(root)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }
}
