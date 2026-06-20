package dev.echo.engine.game;

import dev.echo.engine.api.GameAudio;
import dev.echo.engine.runtime.module.ModuleHost;
import dev.echo.engine.runtime.module.PackManifest;
import dev.echo.engine.test.TestSupport;
import dev.echo.engine.util.SimpleJson;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SaveContentIdentityTest {
    private SaveContentIdentityTest() {
    }

    public static void run(Path packRoot, Path manifestPath) throws Exception {
        Path saves = Files.createTempDirectory("echo-save-identity-test");
        PackManifest manifest = PackManifest.load(manifestPath);
        try (ModuleHost host = new ModuleHost(packRoot, manifest)) {
            host.load();
            GameAudio audio = cue -> { };
            GameSession session = GameSession.createNew("identity", saves, 444L, host, audio);
            awaitReady(session);
            session.save(true);
            session.close();

            Path metadata = saves.resolve("identity/world.json");
            Map<String, Object> root = new LinkedHashMap<>(SimpleJson.readObject(metadata));
            Map<String, Object> identity = new LinkedHashMap<>(object(root.get("contentIdentity")));
            identity.put("contentGraphFingerprint", "0".repeat(64));
            root.put("contentIdentity", identity);
            SimpleJson.write(metadata, root);

            boolean blocked = false;
            try {
                GameSession.load("identity", saves, host, audio);
            } catch (SaveCompatibilityException expected) {
                blocked = expected.blockers().stream().anyMatch(value -> value.contains("Content Graph fingerprint"));
            }
            TestSupport.require(blocked, "save load must fail closed when the canonical graph identity changes");
        }
    }

    private static void awaitReady(GameSession session) throws Exception {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (!session.ready() && System.nanoTime() < deadline) {
            session.updateHeadless(1.0 / 60.0);
            Thread.sleep(5L);
        }
        TestSupport.require(session.ready(), "identity test world should become ready");
    }

    private static Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?> map)) return Map.of();
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }
}
