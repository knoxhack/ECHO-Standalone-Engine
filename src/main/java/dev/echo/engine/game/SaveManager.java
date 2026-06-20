package dev.echo.engine.game;

import dev.echo.engine.runtime.module.ModuleHost;
import dev.echo.engine.runtime.module.PackManifest;
import dev.echo.engine.util.AtomicFiles;
import dev.echo.engine.util.SimpleJson;
import dev.echo.engine.world.VoxelWorld;
import dev.echo.engine.world.WorldStorage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class SaveManager {
    private final WorldStorage chunks = new WorldStorage();

    public Optional<SessionSaveData> read(Path worldRoot) throws IOException {
        Path metadata = worldRoot.resolve("world.json");
        return Files.isRegularFile(metadata)
                ? Optional.of(SessionSaveData.fromJson(SimpleJson.readObject(metadata)))
                : Optional.empty();
    }

    public int loadChunks(Path worldRoot, VoxelWorld world) throws IOException {
        return chunks.loadAll(worldRoot, world);
    }

    public int save(
            Path worldRoot,
            String engineVersion,
            PackManifest pack,
            Player player,
            double time,
            VoxelWorld world,
            ModuleHost modules,
            boolean allChunks
    ) throws IOException {
        LinkedHashMap<String, Map<String, String>> states = new LinkedHashMap<>();
        modules.states().forEach((id, state) -> states.put(id, state.snapshot()));
        SessionSaveData data = new SessionSaveData(
                2,
                engineVersion,
                pack.id(),
                pack.version(),
                modules.contentMap().fingerprint(),
                modules.moduleFingerprints(),
                world.seed(),
                time,
                player.x(),
                player.y(),
                player.z(),
                player.yaw(),
                player.pitch(),
                player.health(),
                player.hunger(),
                player.hydration(),
                player.exposure(),
                player.inventory().selected(),
                player.inventory().toJson(),
                Map.copyOf(states)
        );
        AtomicFiles.replace(
                worldRoot.resolve("world.json"),
                SimpleJson.stringify(data.toJson()).getBytes(StandardCharsets.UTF_8)
        );
        return allChunks ? chunks.saveAll(worldRoot, world) : chunks.saveDirty(worldRoot, world);
    }
}
