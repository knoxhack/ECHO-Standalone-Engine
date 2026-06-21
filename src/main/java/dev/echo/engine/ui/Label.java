package dev.echo.engine.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

public class Label extends Widget {
    private String text;
    private Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
    private Color color = Color.WHITE;

    public Label(String text) {
        this.text = text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setFont(Font font) {
        this.font = font;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    @Override
    public void render(Graphics2D graphics) {
        if (!visible) return;
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setFont(font);
        graphics.setColor(color);
        graphics.drawString(text, x, y + graphics.getFontMetrics().getAscent());
    }
}
