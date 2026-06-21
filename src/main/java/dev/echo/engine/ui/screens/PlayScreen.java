package dev.echo.engine.ui.screens;

import dev.echo.engine.game.GameSession;
import dev.echo.engine.game.HudRenderer;
import dev.echo.engine.game.InputState;
import dev.echo.engine.render.RenderStats;
import dev.echo.engine.render.SoftwareRenderer;
import dev.echo.engine.ui.Screen;
import dev.echo.engine.ui.ScreenManager;

import java.awt.Graphics2D;
import java.awt.event.KeyEvent;

public final class PlayScreen extends Screen {
    private final GameSession session;
    private final InputState input;
    private final SoftwareRenderer renderer = new SoftwareRenderer();
    private final HudRenderer hud = new HudRenderer();
    private boolean debug;
    private RenderStats stats = RenderStats.EMPTY;
    private final Runnable onPause;

    public PlayScreen(ScreenManager manager, GameSession session, InputState input, Runnable onPause) {
        super(manager);
        this.session = session;
        this.input = input;
        this.onPause = onPause;
    }

    @Override
    public boolean rendersWorld() {
        return true;
    }

    @Override
    public boolean capturesMouse() {
        return true;
    }

    @Override
    public void onShow() {
        input.setCaptured(true);
    }

    @Override
    public void onHide() {
        input.setCaptured(false);
    }

    @Override
    public void update(double deltaSeconds) {
        if (input.consumeKey(KeyEvent.VK_F3)) {
            debug = !debug;
        }
        session.update(input, deltaSeconds);
    }

    @Override
    public void render(Graphics2D graphics, int width, int height) {
        double distance = 3 * 16 * 1.9;
        stats = renderer.render(graphics, width, height, session.camera(), session.renderMeshes(), distance);
        hud.worldHud(graphics, width, height, session, stats, debug);
    }

    @Override
    public boolean onKeyPressed(int keyCode) {
        if (keyCode == KeyEvent.VK_ESCAPE && onPause != null) {
            onPause.run();
            return true;
        }
        return false;
    }
}
