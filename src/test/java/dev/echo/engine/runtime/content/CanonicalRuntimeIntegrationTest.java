package dev.echo.engine.runtime.content;

import dev.echo.engine.api.GameExtension;
import dev.echo.engine.api.ResourceId;
import dev.echo.engine.api.adapter.AdapterCoreDescriptorService;
import dev.echo.engine.runtime.adaptercore.AdapterCoreAudit;
import dev.echo.engine.runtime.module.ModuleHost;
import dev.echo.engine.runtime.module.PackManifest;
import dev.echo.engine.test.TestSupport;

import java.nio.file.Path;

public final class CanonicalRuntimeIntegrationTest {
    private CanonicalRuntimeIntegrationTest() {
    }

    public static void run(Path packRoot, Path manifestPath) throws Exception {
        PackManifest manifest = PackManifest.load(manifestPath);
        try (ModuleHost host = new ModuleHost(packRoot, manifest)) {
            host.load();
            TestSupport.require(host.loadedModules().size() == 12, "canonical beta pack should load twelve installed modules");
            TestSupport.require(host.contentMap().modules().size() == 12, "all installed modules must contribute graph bundles");
            TestSupport.require(host.contentMap().nodes().size() >= 70, "canonical graph should merge the Foundation and Ashfall nodes");
            TestSupport.require(host.contentMap().edges().size() >= 50, "canonical graph should preserve semantic relationships");
            TestSupport.require(host.contentMap().parityReport().ready(), "cross-runtime export parity must pass");
            TestSupport.require(host.registries().runtimeBlocks().find(
                    ResourceId.parse("echoashfallprotocol:scorched_ash")
            ).isPresent(), "AdapterCore should materialize graph-defined blocks");
            TestSupport.require(host.registries().runtimeItems().find(
                    ResourceId.parse("echoashfallprotocol:clean_water_bottle")
            ).isPresent(), "AdapterCore should materialize graph-defined items");
            TestSupport.require(host.registries().runtimeRecipes().size() >= 4,
                    "AdapterCore should materialize graph relationship recipes");
            TestSupport.require(host.registries().runtimeWorldGenerators().size() == 1,
                    "Ashfall module should bind exactly one canonical world generator");
            TestSupport.require(host.services().all(GameExtension.class).size() == 1,
                    "Ashfall module should bind exactly one canonical game extension");
            AdapterCoreDescriptorService service = host.services().first(AdapterCoreDescriptorService.class)
                    .orElseThrow();
            TestSupport.require(service.graphFingerprint().equals(host.contentMap().fingerprint()),
                    "AdapterCore service should report the merged graph identity");
            AdapterCoreAudit audit = host.adapterCore().audit();
            TestSupport.require(audit.ready() && audit.rejected() == 0 && audit.accepted() > 20,
                    "AdapterCore mutation ledger should be green and nontrivial");
        }
    }
}
