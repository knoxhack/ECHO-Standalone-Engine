package dev.echo.engine.runtime.module;

import dev.echo.engine.api.ModuleDependency;
import dev.echo.engine.api.ModuleDescriptor;
import dev.echo.engine.runtime.content.ModuleContentBundle;
import dev.echo.engine.test.TestSupport;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ModuleGraphTest {
    private ModuleGraphTest() {
    }

    public static void run() {
        ModuleCandidate base = candidate("base", "1.2.0", List.of());
        ModuleCandidate game = candidate("game", "2.0.0", List.of(new ModuleDependency("base", ">=1.0.0", false)));
        ModuleCandidate ui = candidate("ui", "1.0.0", List.of(new ModuleDependency("game", "^2.0.0", false)));
        List<ModuleCandidate> sorted = ModuleGraph.sort(List.of(ui, game, base));
        TestSupport.require(
                sorted.stream().map(candidate -> candidate.descriptor().id()).toList()
                        .equals(List.of("base", "game", "ui")),
                "module graph topological order"
        );
        boolean missing = false;
        try {
            ModuleGraph.sort(List.of(candidate(
                    "broken", "1.0.0", List.of(new ModuleDependency("missing", "*", false))
            )));
        } catch (IllegalArgumentException expected) {
            missing = true;
        }
        TestSupport.require(missing, "missing required dependency must fail closed");

        boolean incompatible = false;
        try {
            ModuleGraph.sort(List.of(
                    base,
                    candidate("incompatible", "1.0.0", List.of(new ModuleDependency("base", ">=9.0.0", false)))
            ));
        } catch (IllegalArgumentException expected) {
            incompatible = true;
        }
        TestSupport.require(incompatible, "incompatible dependency version must fail closed");
    }

    private static ModuleCandidate candidate(String id, String version, List<ModuleDependency> dependencies) {
        ModuleDescriptor descriptor = new ModuleDescriptor(
                "echo.module.descriptor.v1",
                id,
                id,
                version,
                "",
                "data-only",
                true,
                false,
                dependencies,
                Set.of(),
                Set.of(id),
                List.of(".")
        );
        Path path = Path.of(id + ".jar").toAbsolutePath();
        ModuleContentBundle bundle = new ModuleContentBundle(
                "echo.content_graph.v1", id, path, List.of(), List.of(), List.of(), Map.of(), Map.of(), List.of()
        );
        return new ModuleCandidate(
                new PackModule(id, version, id + ".jar", "", 0L, true, "data-only", "standalone"),
                path,
                descriptor,
                bundle
        );
    }
}
