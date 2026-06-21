package dev.echo.engine.ui;

import java.awt.Graphics2D;

/**
 * Base class for interactive UI widgets.
 */
public abstract class Widget {
    protected int x;
    protected int y;
    protected int width;
    protected int height;
    protected boolean visible = true;
    protected boolean enabled = true;
    protected boolean hovered;
    protected boolean focused;

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public boolean contains(int px, int py) {
        return visible && px >= x && px < x + width && py >= y && py < y + height;
    }

    public boolean onMouseMoved(int px, int py) {
        boolean wasHovered = hovered;
        hovered = contains(px, py);
        return wasHovered != hovered;
    }

    public boolean onMousePressed(int px, int py, int button) {
        if (contains(px, py)) {
            focused = true;
            if (enabled && button == 1) {
                onClick();
                return true;
            }
        } else {
            focused = false;
        }
        return false;
    }

    public void onClick() {}

    public boolean onKeyPressed(int keyCode) {
        return false;
    }

    public abstract void render(Graphics2D graphics);

    public int x() { return x; }
    public int y() { return y; }
    public int width() { return width; }
    public int height() { return height; }
    public void setVisible(boolean visible) { this.visible = visible; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean enabled() { return enabled; }
    public boolean hovered() { return hovered; }
    public boolean focused() { return focused; }
}
