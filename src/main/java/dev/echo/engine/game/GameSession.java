package dev.echo.engine.game;

import dev.echo.engine.api.BlockDefinition;
import dev.echo.engine.api.BlockHit;
import dev.echo.engine.api.GameAccess;
import dev.echo.engine.api.GameAudio;
import dev.echo.engine.api.GameExtension;
import dev.echo.engine.api.InteractionAction;
import dev.echo.engine.api.InteractionResult;
import dev.echo.engine.api.ItemDefinition;
import dev.echo.engine.api.ModuleState;
import dev.echo.engine.api.PlayerAccess;
import dev.echo.engine.api.RecipeDefinition;
import dev.echo.engine.api.ResourceId;
import dev.echo.engine.api.WorldAccess;
import dev.echo.engine.api.event.WorldLoadedEvent;
import dev.echo.engine.render.Camera;
import dev.echo.engine.render.MeshScheduler;
import dev.echo.engine.runtime.adaptercore.AdapterCoreAudit;
import dev.echo.engine.runtime.module.ModuleHost;
import dev.echo.engine.runtime.module.PackManifest;
import dev.echo.engine.world.ChunkPos;
import dev.echo.engine.world.VoxelWorld;
import dev.echo.engine.world.WorldStreamer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class GameSession implements GameAccess, WorldAccess, AutoCloseable {
    public static final String ENGINE_VERSION = "2.0.0-beta.5";

    private final String worldId;
    private final Path worldRoot;
    private final ModuleHost modules;
    private final PackManifest pack;
    private final VoxelWorld world;
    private final WorldStreamer streamer;
    private final MeshScheduler meshes;
    private final Player player;
    private final GameAudio audio;
    private final List<GameExtension> extensions;
    private final EntitySystem entities;
    private final PlayerController controller = new PlayerController();
    private final InteractionSystem interactions = new InteractionSystem();
    private final SaveManager saves = new SaveManager();
    private final ArrayList<GameMessage> messages = new ArrayList<>();
    private double timeSeconds;
    private double autosaveSeconds;
    private boolean activated;
    private final boolean newWorld;

    private GameSession(
            String worldId,
            Path worldRoot,
            ModuleHost modules,
            VoxelWorld world,
            Player player,
            GameAudio audio,
            boolean newWorld
    ) {
        this.worldId = worldId;
        this.worldRoot = worldRoot;
        this.modules = modules;
        this.pack = modules.manifest();
        this.world = world;
        this.player = player;
        this.audio = audio;
        this.newWorld = newWorld;
        extensions = modules.services().all(GameExtension.class);
        entities = new EntitySystem(world, modules.registries().runtimeEntities());
        int processors = Math.max(1, Runtime.getRuntime().availableProcessors());
        streamer = new WorldStreamer(world, modules.registries(), Math.max(1, processors / 3));
        meshes = new MeshScheduler(world, modules.registries().runtimeBlocks(), Math.max(1, processors / 2));
    }

    public static GameSession createNew(
            String worldId,
            Path saveRoot,
            long seed,
            ModuleHost modules,
            GameAudio audio
    ) {
        Inventory inventory = new Inventory(modules.registries().runtimeItems());
        Player player = new Player(inventory);
        player.setPosition(0.5, 42.0, 0.5);
        VoxelWorld world = new VoxelWorld(seed, modules.registries().runtimeBlocks(), modules.events());
        return new GameSession(worldId, saveRoot.resolve(worldId), modules, world, player, audio, true);
    }

    public static GameSession load(
            String worldId,
            Path saveRoot,
            ModuleHost modules,
            GameAudio audio
    ) throws IOException {
        Path root = saveRoot.resolve(worldId);
        SaveManager manager = new SaveManager();
        SessionSaveData data = manager.read(root).orElseThrow(
                () -> new IOException("Save metadata is missing: " + root)
        );
        verifyCompatibility(data, modules);
        modules.restoreStates(data.moduleStates());
        Inventory inventory = new Inventory(modules.registries().runtimeItems());
        inventory.restore(data.inventory());
        inventory.select(data.selectedSlot());
        Player player = new Player(inventory);
        player.setPosition(data.x(), data.y(), data.z());
        player.setYaw(data.yaw());
        player.setPitch(data.pitch());
        player.setHealth(data.health());
        player.setHunger(data.hunger());
        player.setHydration(data.hydration());
        player.setExposure(data.exposure());
        VoxelWorld world = new VoxelWorld(data.seed(), modules.registries().runtimeBlocks(), modules.events());
        manager.loadChunks(root, world);
        GameSession session = new GameSession(worldId, root, modules, world, player, audio, false);
        session.timeSeconds = data.timeSeconds();
        return session;
    }

    private static void verifyCompatibility(SessionSaveData data, ModuleHost modules) throws SaveCompatibilityException {
        ArrayList<String> blockers = new ArrayList<>();
        if (!data.packId().equals(modules.manifest().id())) {
            blockers.add("pack " + data.packId() + " != " + modules.manifest().id());
        }
        if (data.schemaVersion() < 2 || data.contentGraphFingerprint().isBlank()) {
            blockers.add("legacy save has no canonical Content Graph fingerprint");
        } else if (!data.contentGraphFingerprint().equals(modules.contentMap().fingerprint())) {
            blockers.add("Content Graph fingerprint changed");
        }
        Map<String, String> current = modules.moduleFingerprints();
        LinkedHashSet<String> moduleIds = new LinkedHashSet<>(data.moduleFingerprints().keySet());
        moduleIds.addAll(current.keySet());
        for (String moduleId : moduleIds) {
            String saved = data.moduleFingerprints().get(moduleId);
            String installed = current.get(moduleId);
            if (saved == null) blockers.add("new installed module " + moduleId);
            else if (installed == null) blockers.add("missing saved module " + moduleId);
            else if (!saved.equalsIgnoreCase(installed)) blockers.add("module bytes changed: " + moduleId);
        }
        if (!blockers.isEmpty()) {
            throw new SaveCompatibilityException(
                    "Save content identity is incompatible; world files were not modified",
                    blockers
            );
        }
    }

    public void updateLoading(double deltaSeconds) {
        timeSeconds += Math.max(0.0, deltaSeconds);
        streamer.update(player.x(), player.z(), 3);
        meshes.syncVisible(player.x(), player.z(), 3);
        activateIfReady();
    }

    public void update(InputState input, double deltaSeconds) {
        double dt = Math.max(0.0, deltaSeconds);
        timeSeconds += dt;
        streamer.update(player.x(), player.z(), 3);
        meshes.syncVisible(player.x(), player.z(), 3);
        if (!activateIfReady()) return;
        controller.update(player, world, input, dt);
        interactions.update(this, input, dt);
        for (GameExtension extension : extensions) extension.tick(this, dt);
        entities.tick(player, dt);
        autosaveSeconds += dt;
        if (autosaveSeconds >= 60.0) {
            autosaveSeconds = 0.0;
            try {
                save(false);
                message("Autosaved");
            } catch (IOException failure) {
                message("Autosave failed: " + failure.getMessage());
            }
        }
        pruneMessages();
    }

    public void updateHeadless(double deltaSeconds) {
        double dt = Math.max(0.0, deltaSeconds);
        timeSeconds += dt;
        streamer.update(player.x(), player.z(), 2);
        meshes.syncVisible(player.x(), player.z(), 2);
        if (!activateIfReady()) return;
        for (GameExtension extension : extensions) extension.tick(this, dt);
        entities.tick(player, dt);
        pruneMessages();
    }

    private boolean activateIfReady() {
        if (activated) return true;
        ChunkPos center = ChunkPos.fromBlock((int) Math.floor(player.x()), (int) Math.floor(player.z()));
        if (!world.hasChunk(center)) return false;
        if (newWorld) player.setPosition(0.5, world.surfaceY(0, 0) + 2.05, 0.5);
        for (GameExtension extension : extensions) extension.onSessionStart(this);
        modules.events().publish(new WorldLoadedEvent(world.seed(), worldId));
        activated = true;
        message(newWorld ? "World generated from canonical module graph" : "World loaded with matching content identity");
        return true;
    }

    public boolean ready() {
        return activated;
    }

    public void save(boolean allChunks) throws IOException {
        Files.createDirectories(worldRoot);
        saves.save(worldRoot, ENGINE_VERSION, pack, player, timeSeconds, world, modules, allChunks);
    }

    public InteractionResult invokeInteraction(BlockHit hit, InteractionAction action) {
        for (GameExtension extension : extensions) {
            InteractionResult result = extension.interact(this, hit, action);
            if (result != null && result.handled()) return result;
        }
        return InteractionResult.pass();
    }

    public void craftFirstAvailable() {
        for (RecipeDefinition recipe : modules.registries().runtimeRecipes().values()) {
            boolean possible = recipe.ingredients().entrySet().stream()
                    .allMatch(entry -> player.inventory().count(entry.getKey()) >= entry.getValue());
            if (!possible) continue;
            recipe.ingredients().forEach((id, count) -> player.inventory().consume(id, count));
            int remaining = player.inventory().add(recipe.result(), recipe.resultCount());
            if (remaining > 0) message("Inventory full; crafted items were lost");
            else message("Crafted " + modules.registries().runtimeItems().require(recipe.result()).displayName());
            audio.play("craft.complete");
            return;
        }
        message("No available recipe");
    }

    public String attackEntity() {
        return entities.attackFrom(player, 6.0);
    }

    public java.util.Collection<dev.echo.engine.render.ChunkMesh> renderMeshes() {
        ArrayList<dev.echo.engine.render.ChunkMesh> all = new ArrayList<>(meshes.meshes());
        if (!entities.entities().isEmpty()) all.add(entities.renderMesh());
        return List.copyOf(all);
    }

    public int entityCount() {
        return entities.entities().size();
    }

    public Camera camera() {
        return new Camera(player.x(), player.eyeY(), player.z(), player.yaw(), player.pitch(), 72.0);
    }

    public VoxelWorld voxelWorld() {
        return world;
    }

    public MeshScheduler meshScheduler() {
        return meshes;
    }

    public Player playerEntity() {
        return player;
    }

    public BlockHit target() {
        return interactions.target();
    }

    public double breakProgress() {
        return interactions.breakProgress();
    }

    public PackManifest pack() {
        return pack;
    }

    public String worldId() {
        return worldId;
    }

    public int pendingChunks() {
        return streamer.pendingCount();
    }

    public String contentGraphFingerprint() {
        return modules.contentMap().fingerprint();
    }

    public int contentGraphModules() {
        return modules.contentMap().modules().size();
    }

    public int contentGraphNodes() {
        return modules.contentMap().nodes().size();
    }

    public int contentGraphEdges() {
        return modules.contentMap().edges().size();
    }

    public AdapterCoreAudit adapterCoreAudit() {
        return modules.adapterCore().audit();
    }

    BlockDefinition blockDefinition(ResourceId id) {
        return modules.registries().runtimeBlocks().require(id);
    }

    ItemDefinition itemDefinition(ResourceId id) {
        return modules.registries().runtimeItems().find(id).orElse(null);
    }

    public List<String> extensionHudLines() {
        ArrayList<String> result = new ArrayList<>();
        for (GameExtension extension : extensions) result.addAll(extension.hudLines(this));
        return List.copyOf(result);
    }

    public List<GameMessage> activeMessages() {
        pruneMessages();
        return List.copyOf(messages);
    }

    private void pruneMessages() {
        messages.removeIf(message -> message.expiresAt() <= timeSeconds);
    }

    @Override
    public PlayerAccess player() {
        return player;
    }

    @Override
    public WorldAccess world() {
        return this;
    }

    @Override
    public ModuleState state(String moduleId) {
        return modules.state(moduleId);
    }

    @Override
    public GameAudio audio() {
        return audio;
    }

    @Override
    public long seed() {
        return world.seed();
    }

    @Override
    public double timeSeconds() {
        return timeSeconds;
    }

    @Override
    public void message(String text) {
        if (text == null || text.isBlank()) return;
        messages.add(new GameMessage(text, timeSeconds + 4.0));
        while (messages.size() > 5) messages.remove(0);
        System.out.println("[game] " + text);
    }

    @Override
    public ResourceId blockAt(int x, int y, int z) {
        return world.blockIdAt(x, y, z);
    }

    @Override
    public boolean setBlock(int x, int y, int z, ResourceId id) {
        return world.setBlock(x, y, z, id);
    }

    @Override
    public boolean isBlockNear(ResourceId id, double x, double y, double z, int radius) {
        return world.isBlockNear(id, x, y, z, radius);
    }

    @Override
    public int surfaceY(int x, int z) {
        return world.surfaceY(x, z);
    }

    @Override
    public void close() {
        try {
            save(false);
        } catch (Exception failure) {
            System.err.println("[save] final save failed: " + failure.getMessage());
        }
        meshes.close();
        streamer.close();
    }
}
