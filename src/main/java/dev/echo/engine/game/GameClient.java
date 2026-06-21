package dev.echo.engine.game;

import dev.echo.engine.runtime.module.ModuleHost;
import dev.echo.engine.runtime.module.PackManifest;
import dev.echo.engine.ui.ScreenManager;
import dev.echo.engine.ui.screens.ErrorScreen;
import dev.echo.engine.ui.screens.LoadingScreen;
import dev.echo.engine.ui.screens.PauseScreen;
import dev.echo.engine.ui.screens.PlayScreen;
import dev.echo.engine.ui.screens.TitleScreen;

import java.awt.Color;
import java.awt.Graphics2D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class GameClient implements AutoCloseable {
    private final LaunchOptions options;
    private final PackManifest pack;
    private final ModuleHost modules;
    private final JavaSoundEngine audio = new JavaSoundEngine();
    private final InputState input = new InputState();
    private final GameWindow window;
    private final ScreenManager screens = new ScreenManager();
    private GameSession session;
    private String pendingError;

    public GameClient(LaunchOptions options) throws Exception {
        this.options = options;
        this.pack = PackManifest.load(options.manifest());
        this.modules = new ModuleHost(options.packRoot(), pack);
        modules.load();
        this.window = new GameWindow(1280, 720, input);
        screens.push(new TitleScreen(screens, this, lastWorldExists(), ""));
    }

    public PackManifest manifest() {
        return pack;
    }

    public ModuleHost modules() {
        return modules;
    }

    public PlayScreen createPlayScreen(GameSession session) {
        return new PlayScreen(screens, session, input, () -> screens.push(new PauseScreen(screens, this)));
    }

    public void startNew() {
        try {
            closeSession();
            String worldId = "world-" + System.currentTimeMillis();
            session = GameSession.createNew(worldId, options.saveRoot(), System.nanoTime(), modules, audio);
            Files.createDirectories(options.saveRoot());
            Files.writeString(options.saveRoot().resolve("last-world.txt"), worldId);
            screens.replace(new LoadingScreen(screens, this, session));
            pendingError = null;
        } catch (Exception failure) {
            fail(failure);
        }
    }

    public void continueLast() {
        try {
            Path marker = options.saveRoot().resolve("last-world.txt");
            if (!Files.isRegularFile(marker)) {
                pendingError = "No saved world found";
                return;
            }
            String worldId = Files.readString(marker).trim();
            closeSession();
            session = GameSession.load(worldId, options.saveRoot(), modules, audio);
            screens.replace(new LoadingScreen(screens, this, session));
            pendingError = null;
        } catch (Exception failure) {
            fail(failure);
        }
    }

    public void saveWorld() {
        if (session == null) return;
        try {
            session.save(true);
            session.message("Saved world");
        } catch (IOException failure) {
            session.message("Save failed: " + failure.getMessage());
        }
    }

    public void returnToTitle() {
        closeSession();
        screens.clearAndSet(new TitleScreen(screens, this, lastWorldExists(), ""));
    }

    public void shutdown() {
        window.requestClose();
    }

    public void run() {
        double previous = now();
        double accumulator = 0;
        final double step = 1.0 / 60.0;
        while (!window.shouldClose()) {
            double frameStart = now();
            double elapsed = Math.min(0.25, frameStart - previous);
            previous = frameStart;
            accumulator += elapsed;
            int updates = 0;
            while (accumulator >= step && updates < 5) {
                update(step);
                accumulator -= step;
                updates++;
            }
            if (updates == 5 && accumulator >= step) {
                accumulator %= step;
            }
            window.render(this::render);
            screens.applyPending();
            double remaining = step - (now() - frameStart);
            if (remaining > 0.001) {
                try {
                    Thread.sleep((long) (remaining * 1000));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void update(double dt) {
        updateInputCapture();
        dispatchInput();
        screens.update(dt);
        if (pendingError != null) {
            screens.clearAndSet(new ErrorScreen(screens, this, pendingError));
            pendingError = null;
        }
    }

    private void updateInputCapture() {
        boolean wantsCapture = screens.isGameFocused();
        if (input.captured() != wantsCapture) {
            input.setCaptured(wantsCapture);
            if (!wantsCapture) {
                input.releaseAll();
            }
        }
    }

    private void dispatchInput() {
        input.dispatchKeyPresses(code -> screens.onKeyPressed(code));
        screens.onMouseMoved(input.mouseX(), input.mouseY());
        for (int button = 1; button <= 3; button++) {
            if (input.consumeMouse(button)) {
                screens.onMousePressed(input.mouseX(), input.mouseY(), button);
            }
        }
        int wheel = input.consumeWheel();
        if (wheel != 0) {
            screens.onMouseWheel(wheel);
        }
    }

    private void render(Graphics2D g, int width, int height) {
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, width, height);
        screens.render(g, width, height);
    }

    private boolean lastWorldExists() {
        try {
            Path marker = options.saveRoot().resolve("last-world.txt");
            if (!Files.isRegularFile(marker)) return false;
            String id = Files.readString(marker).trim();
            return Files.isRegularFile(options.saveRoot().resolve(id).resolve("world.json"));
        } catch (IOException ignored) {
            return false;
        }
    }

    private void closeSession() {
        if (session != null) {
            session.close();
            session = null;
        }
    }

    private void fail(Exception failure) {
        failure.printStackTrace(System.err);
        pendingError = failure.getMessage();
    }

    private static double now() {
        return System.nanoTime() / 1_000_000_000.0;
    }

    @Override
    public void close() {
        closeSession();
        try {
            modules.close();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
        audio.close();
        window.close();
    }
}
