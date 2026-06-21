package dev.echo.engine.ui;

import java.awt.Graphics2D;

/**
 * A single screen in the Engine screen stack. The top screen receives update, render, and input events.
 */
public abstract class Screen {
    protected final ScreenManager manager;

    protected Screen(ScreenManager manager) {
        this.manager = manager;
    }

    /** Called when this screen becomes the top screen. */
    public void onShow() {}

    /** Called when this screen is no longer the top screen. */
    public void onHide() {}

    /** Per-frame update. */
    public void update(double deltaSeconds) {}

    /** Render the screen. */
    public abstract void render(Graphics2D graphics, int width, int height);

    /** Return true if the game world should still be rendered underneath this screen. */
    public boolean rendersWorld() {
        return false;
    }

    /** Return true if the game should capture the mouse while this screen is on top. */
    public boolean capturesMouse() {
        return false;
    }

    public boolean onKeyPressed(int keyCode) {
        return false;
    }

    public boolean onKeyReleased(int keyCode) {
        return false;
    }

    public boolean onMouseMoved(int x, int y) {
        return false;
    }

    public boolean onMousePressed(int x, int y, int button) {
        return false;
    }

    public boolean onMouseReleased(int x, int y, int button) {
        return false;
    }

    public boolean onMouseWheel(int rotation) {
        return false;
    }

    public void onResize(int width, int height) {}
}
