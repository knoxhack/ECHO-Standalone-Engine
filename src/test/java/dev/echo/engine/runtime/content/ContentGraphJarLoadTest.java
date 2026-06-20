package dev.echo.engine.runtime.content;

import dev.echo.engine.test.TestSupport;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

public final class ContentGraphJarLoadTest {
    private ContentGraphJarLoadTest() {
    }

    public static void run(Path packRoot) throws Exception {
        Path ashfall = packRoot.resolve("mods/echoashfallprotocol-1.0.0-standalone.jar");
        ModuleContentBundle bundle = new ModuleContentGraphLoader().load(ashfall, "echoashfallprotocol", true);
        TestSupport.require(bundle.nodes().size() >= 40, "Ashfall graph should expose its full semantic node set");
        TestSupport.require(bundle.edges().size() >= 30, "Ashfall graph should expose cross-content relationships");
        TestSupport.require(bundle.exportPlans().keySet().containsAll(
                CanonicalContentMap.CROSS_RUNTIME_TARGETS
        ), "Ashfall JAR should embed Native, NeoForge, and Standalone export plans");
        TestSupport.require(bundle.unresolvedReferences().isEmpty(), "strict Ashfall graph should have no unresolved references");

        Path invalid = Files.createTempFile("echo-graph-missing-export", ".jar");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(invalid))) {
            put(jar, ModuleContentGraphLoader.GRAPH_PATH, """
                    {"schemaVersion":"echo.content_graph.v1","moduleId":"invalid","nodes":[
                      {"id":"invalid:item/test","kind":"ITEM","displayName":"Test"}
                    ],"edges":[]}
                    """);
            put(jar, ModuleContentGraphLoader.FEATURES_PATH, "{\"features\":[]}");
            put(jar, ModuleContentGraphLoader.PROVENANCE_PATH, "{}");
            put(jar, ModuleContentGraphLoader.UNRESOLVED_PATH, "[]");
        }
        boolean failedClosed = false;
        try {
            new ModuleContentGraphLoader().load(invalid, "invalid", true);
        } catch (GraphValidationException expected) {
            failedClosed = true;
        }
        TestSupport.require(failedClosed, "strict graph loading must reject a JAR without standalone export data");
    }

    private static void put(JarOutputStream jar, String name, String value) throws Exception {
        JarEntry entry = new JarEntry(name);
        jar.putNextEntry(entry);
        jar.write(value.getBytes(StandardCharsets.UTF_8));
        jar.closeEntry();
    }
}
