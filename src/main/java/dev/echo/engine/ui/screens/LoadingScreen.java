package dev.echo.engine.ui.screens;

import dev.echo.engine.game.GameClient;
import dev.echo.engine.game.GameSession;
import dev.echo.engine.ui.Label;
import dev.echo.engine.ui.Screen;
import dev.echo.engine.ui.ScreenManager;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;

public final class LoadingScreen extends Screen {
    private final GameClient client;
    private final GameSession session;
    private final Label heading;
    private final Label detail;

    public LoadingScreen(ScreenManager manager, GameClient client, GameSession session) {
        super(manager);
        this.client = client;
        this.session = session;
        heading = new Label("Generating graph-defined Ashlands…");
        heading.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        heading.setColor(Color.WHITE);
        detail = new Label("");
        detail.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        detail.setColor(new Color(210, 210, 210));
    }

    @Override
    public void update(double deltaSeconds) {
        session.updateLoading(deltaSeconds);
        if (session.ready()) {
            manager.replace(client.createPlayScreen(session));
        }
    }

    @Override
    public void render(Graphics2D graphics, int width, int height) {
        graphics.setColor(new Color(0, 0, 0, 150));
        graphics.fillRect(0, 0, width, height);
        heading.setBounds((width - 400) / 2, height / 2 - 20, 400, 40);
        heading.render(graphics);
        detail.setText("chunk jobs " + session.pendingChunks() + "  mesh jobs " + session.meshScheduler().pendingCount());
        detail.setBounds((width - 400) / 2, height / 2 + 20, 400, 30);
        detail.render(graphics);
    }
}
