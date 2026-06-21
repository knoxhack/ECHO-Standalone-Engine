package dev.echo.engine.ui.screens;

import dev.echo.engine.game.GameClient;
import dev.echo.engine.ui.Button;
import dev.echo.engine.ui.Label;
import dev.echo.engine.ui.Screen;
import dev.echo.engine.ui.ScreenManager;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;

public final class ErrorScreen extends Screen {
    private final GameClient client;
    private final String message;
    private final Label heading;
    private final Label detail;
    private final Button closeButton;

    public ErrorScreen(ScreenManager manager, GameClient client, String message) {
        super(manager);
        this.client = client;
        this.message = message;
        heading = new Label("ECHO ENGINE ERROR");
        heading.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
        heading.setColor(new Color(255, 120, 120));
        detail = new Label(message == null ? "Unknown failure" : message);
        detail.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        detail.setColor(Color.WHITE);
        closeButton = new Button("Close", client::shutdown);
    }

    @Override
    public void render(Graphics2D graphics, int width, int height) {
        graphics.setColor(new Color(28, 8, 8));
        graphics.fillRect(0, 0, width, height);
        heading.setBounds((width - 400) / 2, height / 3 - 20, 400, 50);
        heading.render(graphics);
        detail.setBounds((width - 600) / 2, height / 2 - 10, 600, 60);
        detail.render(graphics);
        closeButton.setBounds((width - 160) / 2, height / 2 + 80, 160, 36);
        closeButton.render(graphics);
    }

    @Override
    public boolean onMouseMoved(int x, int y) {
        closeButton.onMouseMoved(x, y);
        return true;
    }

    @Override
    public boolean onMousePressed(int x, int y, int button) {
        return closeButton.onMousePressed(x, y, button);
    }
}
