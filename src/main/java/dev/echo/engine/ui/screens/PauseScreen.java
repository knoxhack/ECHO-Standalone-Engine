package dev.echo.engine.ui.screens;

import dev.echo.engine.game.GameClient;
import dev.echo.engine.ui.Button;
import dev.echo.engine.ui.Label;
import dev.echo.engine.ui.Screen;
import dev.echo.engine.ui.ScreenManager;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;

public final class PauseScreen extends Screen {
    private final GameClient client;
    private final Button resumeButton;
    private final Button saveButton;
    private final Button returnButton;
    private final Label titleLabel;

    public PauseScreen(ScreenManager manager, GameClient client) {
        super(manager);
        this.client = client;
        titleLabel = new Label("PAUSED");
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 44));
        titleLabel.setColor(Color.WHITE);

        resumeButton = new Button("Resume", () -> manager.pop());
        saveButton = new Button("Save", client::saveWorld);
        returnButton = new Button("Return To Title", client::returnToTitle);
    }

    @Override
    public boolean rendersWorld() {
        return true;
    }

    @Override
    public void render(Graphics2D graphics, int width, int height) {
        graphics.setColor(new Color(0, 0, 0, 170));
        graphics.fillRect(0, 0, width, height);

        titleLabel.setBounds((width - 200) / 2, height / 3 - 40, 200, 60);
        titleLabel.render(graphics);

        int buttonWidth = 220;
        int buttonHeight = 40;
        int x = (width - buttonWidth) / 2;
        int y = height / 2 - 30;
        int gap = 52;

        resumeButton.setBounds(x, y, buttonWidth, buttonHeight);
        saveButton.setBounds(x, y + gap, buttonWidth, buttonHeight);
        returnButton.setBounds(x, y + gap * 2, buttonWidth, buttonHeight);

        resumeButton.render(graphics);
        saveButton.render(graphics);
        returnButton.render(graphics);
    }

    @Override
    public boolean onMouseMoved(int x, int y) {
        resumeButton.onMouseMoved(x, y);
        saveButton.onMouseMoved(x, y);
        returnButton.onMouseMoved(x, y);
        return true;
    }

    @Override
    public boolean onMousePressed(int x, int y, int button) {
        return resumeButton.onMousePressed(x, y, button)
                || saveButton.onMousePressed(x, y, button)
                || returnButton.onMousePressed(x, y, button);
    }

    @Override
    public boolean onKeyPressed(int keyCode) {
        return false;
    }
}
