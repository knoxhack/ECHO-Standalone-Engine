package dev.echo.engine.runtime.adaptercore;

import dev.echo.engine.api.EchoModuleContext;
import dev.echo.engine.api.ModuleDescriptor;
import dev.echo.engine.api.ServiceRegistry;
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
import java.util.concurrent.atomic.AtomicInteger;

public final class AdapterCoreBridgeSurfaceTest {
    private AdapterCoreBridgeSurfaceTest() {
    }

    public static void run(Path packRoot, Path manifestPath) throws Exception {
        boolean mutableServiceMethod = List.of(ServiceRegistry.class.getMethods()).stream()
                .anyMatch(method -> method.getName().equals("register"));
        TestSupport.require(
                !mutableServiceMethod,
                "module-facing ServiceRegistry must be read-only");
        boolean directEventBus = List.of(EchoModuleContext.class.getMethods()).stream()
                .anyMatch(method -> method.getName().equals("events"));
        TestSupport.require(
                !directEventBus,
                "module context must not expose a direct event mutation surface");

        PackManifest manifest = PackManifest.load(manifestPath);
        ModuleContentGraphLoader loader = new ModuleContentGraphLoader();
        ArrayList<ModuleContentBundle> bundles = new ArrayList<>();
        for (var module : manifest.modules()) {
            bundles.add(loader.load(packRoot.resolve(module.file()), module.id(), true));
        }
        var graph = new ContentGraphMerger().merge(bundles, true);
        DefaultEventBus eventBus = new DefaultEventBus();
        AdapterCoreRuntime runtime = new AdapterCoreRuntime(
                graph,
                new DefaultContentRegistries(),
                new DefaultServiceRegistry(),
                eventBus);
        ModuleDescriptor descriptor = new ModuleDescriptor(
                "echo.module.descriptor.v1",
                "echoashfallprotocol",
                "Ashfall",
                "1.0.0",
                "",
                "official",
                true,
                true,
                List.of(),
                Set.of("adaptercore.events"),
                Set.of("echoashfallprotocol"),
                List.of("."));

        AtomicInteger events = new AtomicInteger();
        var receipt = runtime.session(descriptor).subscribeEvent(
                "echoashfallprotocol:system/ashfall_runtime",
                String.class,
                ignored -> events.incrementAndGet());
        TestSupport.require(receipt.accepted(), "owned graph-backed event subscription should be accepted");
        eventBus.publish("first");
        TestSupport.require(events.get() == 1, "accepted AdapterCore event subscription should receive events");
        runtime.rollbackModule(descriptor.id());
        eventBus.publish("second");
        TestSupport.require(events.get() == 1, "rollback should revoke AdapterCore event subscription");
        TestSupport.require(
                runtime.audit().revoked() == 1,
                "event rollback should be recorded as a revoked mutation receipt");
        runtime.close();
    }
}
