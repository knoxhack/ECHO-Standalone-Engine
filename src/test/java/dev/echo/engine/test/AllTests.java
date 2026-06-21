package dev.echo.engine.test;

import dev.echo.engine.game.EngineCliTest;
import dev.echo.engine.game.RenderSmokeTest;
import dev.echo.engine.game.SaveContentIdentityTest;
import dev.echo.engine.runtime.adaptercore.AdapterCoreBridgeSurfaceTest;
import dev.echo.engine.runtime.adaptercore.AdapterCoreMutationPolicyTest;
import dev.echo.engine.runtime.content.CanonicalRuntimeIntegrationTest;
import dev.echo.engine.runtime.content.ContentGraphJarLoadTest;
import dev.echo.engine.runtime.module.ModuleGraphTest;
import dev.echo.engine.util.SimpleJsonTest;
import dev.echo.engine.world.ChunkPaletteSaveTest;

import java.nio.file.Path;

public final class AllTests {
    private AllTests() {
    }

    public static void main(String[] args) throws Exception {
        Path packRoot = args.length > 0
                ? Path.of(args[0]).toAbsolutePath().normalize()
                : Path.of("dist").toAbsolutePath().normalize();
        Path manifest = args.length > 1
                ? Path.of(args[1]).toAbsolutePath().normalize()
                : packRoot.resolve("pack.json");
        SimpleJsonTest.run();
        ModuleGraphTest.run();
        ChunkPaletteSaveTest.run();
        ContentGraphJarLoadTest.run(packRoot);
        AdapterCoreMutationPolicyTest.run(packRoot, manifest);
        AdapterCoreBridgeSurfaceTest.run(packRoot, manifest);
        CanonicalRuntimeIntegrationTest.run(packRoot, manifest);
        SaveContentIdentityTest.run(packRoot, manifest);
        EngineCliTest.run(packRoot, manifest);
        RenderSmokeTest.run(packRoot, manifest);
        System.out.println("ALL TESTS PASS");
    }
}
