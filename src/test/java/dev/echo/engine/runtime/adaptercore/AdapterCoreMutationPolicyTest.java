package dev.echo.engine.runtime.adaptercore;

import dev.echo.engine.api.ModuleDescriptor;
import dev.echo.engine.api.adapter.MutationReceipt;
import dev.echo.engine.runtime.content.ContentGraphMerger;
import dev.echo.engine.runtime.content.ModuleContentBundle;
import dev.echo.engine.runtime.content.ModuleContentGraphLoader;
import dev.echo.engine.runtime.module.DefaultEventBus;
import dev.echo.engine.runtime.module.DefaultServiceRegistry;
import dev.echo.engine.runtime.module.PackManifest;
import dev.echo.engine.runtime.registry.DefaultContentRegistries;
import dev.echo.engine.test.TestSupport;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class AdapterCoreMutationPolicyTest {
    private AdapterCoreMutationPolicyTest() {
    }

    public static void run(Path packRoot, Path manifestPath) throws Exception {
        ModuleContentGraphLoader loader = new ModuleContentGraphLoader();
        PackManifest manifest = PackManifest.load(manifestPath);
        ArrayList<ModuleContentBundle> bundles = new ArrayList<>();
        for (var module : manifest.modules()) {
            bundles.add(loader.load(packRoot.resolve(module.file()), module.id(), true));
        }
        var graph = new ContentGraphMerger().merge(bundles, true);
        AdapterCoreRuntime runtime = new AdapterCoreRuntime(
                graph, new DefaultContentRegistries(), new DefaultServiceRegistry(), new DefaultEventBus()
        );
        ModuleDescriptor hostile = new ModuleDescriptor(
                "echo.module.descriptor.v1",
                "echoashfallprotocol",
                "Ashfall",
                "1.0.0",
                "",
                "official",
                true,
                true,
                List.of(),
                Set.of("adaptercore.worldgen"),
                Set.of("echoashfallprotocol"),
                List.of(".")
        );
        MutationReceipt foreign = runtime.session(hostile).bindWorldGenerator(
                "echoadaptercore:capability/runtime_bridge",
                1,
                (chunk, seed, chunkX, chunkZ) -> { }
        );
        TestSupport.require(!foreign.accepted(), "module must not bind behavior to a foreign AdapterCore graph node");
        TestSupport.require(runtime.audit().rejected() == 1, "rejected mutation must be preserved in the ledger");
        runtime.close();
    }
}
