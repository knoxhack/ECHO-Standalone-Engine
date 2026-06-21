package dev.echo.engine.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

public class Button extends Widget {
    private String text;
    private Runnable action;
    private Font font = new Font(Font.SANS_SERIF, Font.BOLD, 16);
    private Color background = new Color(40, 48, 56);
    private Color backgroundHover = new Color(64, 80, 96);
    private Color backgroundDisabled = new Color(30, 30, 30);
    private Color border = new Color(140, 160, 180);
    private Color textColor = Color.WHITE;
    private Color textColorDisabled = new Color(120, 120, 120);

    public Button(String text, Runnable action) {
        this.text = text;
        this.action = action;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setAction(Runnable action) {
        this.action = action;
    }

    @Override
    public void onClick() {
        if (action != null) {
            action.run();
        }
    }

    @Override
    public void render(Graphics2D graphics) {
        if (!visible) return;
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Color fill = enabled ? (hovered ? backgroundHover : background) : backgroundDisabled;
        graphics.setColor(fill);
        graphics.fillRoundRect(x, y, width, height, 8, 8);
        graphics.setColor(enabled ? border : new Color(80, 80, 80));
        graphics.drawRoundRect(x, y, width, height, 8, 8);
        graphics.setFont(font);
        graphics.setColor(enabled ? textColor : textColorDisabled);
        int tw = graphics.getFontMetrics().stringWidth(text);
        graphics.drawString(text, x + (width - tw) / 2, y + height / 2 + 5);
    }
}
