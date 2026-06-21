package dev.echo.engine.ui.screens;

import dev.echo.engine.game.GameClient;
import dev.echo.engine.game.GameSession;
import dev.echo.engine.ui.Button;
import dev.echo.engine.ui.Label;
import dev.echo.engine.ui.Screen;
import dev.echo.engine.ui.ScreenManager;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

public final class TitleScreen extends Screen {
    private final GameClient client;
    private final Button continueButton;
    private final Button newWorldButton;
    private final Button quitButton;
    private final Label titleLabel;
    private final Label subtitleLabel;
    private final Label graphLabel;
    private final Label mutationLabel;
    private final Label fingerprintLabel;
    private String error;

    public TitleScreen(ScreenManager manager, GameClient client, boolean canContinue, String error) {
        super(manager);
        this.client = client;
        this.error = error;

        titleLabel = new Label("ECHO STANDALONE");
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 44));
        titleLabel.setColor(new Color(225, 140, 72));

        subtitleLabel = new Label(client.manifest().name() + "  " + client.manifest().version());
        subtitleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        subtitleLabel.setColor(Color.WHITE);

        graphLabel = new Label("");
        mutationLabel = new Label("");
        fingerprintLabel = new Label("");
        graphLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        mutationLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        fingerprintLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        graphLabel.setColor(new Color(150, 190, 180));
        mutationLabel.setColor(new Color(150, 170, 180));
        fingerprintLabel.setColor(new Color(150, 170, 180));

        continueButton = new Button("Continue", client::continueLast);
        continueButton.setEnabled(canContinue);
        newWorldButton = new Button("New World", client::startNew);
        quitButton = new Button("Quit", client::shutdown);
    }

    public void setError(String error) {
        this.error = error;
    }

    @Override
    public void onShow() {
        graphLabel.setText("Canonical graph: " + client.modules().contentMap().modules().size() + " modules  "
                + client.modules().contentMap().nodes().size() + " nodes  "
                + client.modules().contentMap().edges().size() + " edges");
        mutationLabel.setText("AdapterCore mutations: " + client.modules().adapterCore().audit().accepted() + " accepted / "
                + client.modules().adapterCore().audit().rejected() + " rejected");
        fingerprintLabel.setText("Graph " + shortFingerprint(client.modules().contentMap().fingerprint()));
    }

    @Override
    public void render(Graphics2D graphics, int width, int height) {
        graphics.setColor(new Color(20, 24, 28));
        graphics.fillRect(0, 0, width, height);

        titleLabel.setBounds((width - 380) / 2, height / 4 - 40, 380, 60);
        subtitleLabel.setBounds((width - 380) / 2, height / 4 + 20, 380, 40);
        titleLabel.render(graphics);
        subtitleLabel.render(graphics);

        int buttonWidth = 220;
        int buttonHeight = 40;
        int x = (width - buttonWidth) / 2;
        int y = height / 2 - 30;
        int gap = 52;

        continueButton.setBounds(x, y, buttonWidth, buttonHeight);
        newWorldButton.setBounds(x, y + gap, buttonWidth, buttonHeight);
        quitButton.setBounds(x, y + gap * 2, buttonWidth, buttonHeight);

        continueButton.render(graphics);
        newWorldButton.render(graphics);
        quitButton.render(graphics);

        graphLabel.setBounds((width - 600) / 2, height - 100, 600, 20);
        mutationLabel.setBounds((width - 600) / 2, height - 78, 600, 20);
        fingerprintLabel.setBounds((width - 600) / 2, height - 56, 600, 20);
        graphLabel.render(graphics);
        mutationLabel.render(graphics);
        fingerprintLabel.render(graphics);

        if (error != null && !error.isBlank()) {
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            graphics.setColor(new Color(255, 110, 110));
            String text = error;
            int tw = graphics.getFontMetrics().stringWidth(text);
            graphics.drawString(text, (width - tw) / 2, height - 28);
        }
    }

    @Override
    public boolean onMouseMoved(int x, int y) {
        continueButton.onMouseMoved(x, y);
        newWorldButton.onMouseMoved(x, y);
        quitButton.onMouseMoved(x, y);
        return true;
    }

    @Override
    public boolean onMousePressed(int x, int y, int button) {
        return continueButton.onMousePressed(x, y, button)
                || newWorldButton.onMousePressed(x, y, button)
                || quitButton.onMousePressed(x, y, button);
    }

    @Override
    public boolean onMouseReleased(int x, int y, int button) {
        return true;
    }

    @Override
    public boolean onKeyPressed(int keyCode) {
        return false;
    }

    private static String shortFingerprint(String value) {
        return value == null || value.length() <= 12 ? String.valueOf(value) : value.substring(0, 12);
    }
}
