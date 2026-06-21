package dev.echo.engine.ui;

import java.awt.Graphics2D;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;

/**
 * Stack-based screen manager. The top screen receives input and render calls.
 */
public final class ScreenManager {
    private final Deque<Screen> stack = new ArrayDeque<>();
    private Supplier<Screen> pendingReplace;
    private boolean pendingPop;

    public void push(Screen screen) {
        if (!stack.isEmpty()) {
            stack.peek().onHide();
        }
        stack.push(screen);
        screen.onShow();
    }

    public void pop() {
        pendingPop = true;
    }

    public void replace(Screen screen) {
        pendingReplace = () -> screen;
    }

    public void clearAndSet(Screen screen) {
        while (!stack.isEmpty()) {
            stack.pop().onHide();
        }
        stack.push(screen);
        screen.onShow();
    }

    public Screen top() {
        return stack.peek();
    }

    public boolean isEmpty() {
        return stack.isEmpty();
    }

    public boolean isGameFocused() {
        Screen top = stack.peek();
        return top != null && top.rendersWorld() && top.capturesMouse();
    }

    public void update(double deltaSeconds) {
        Screen top = stack.peek();
        if (top != null) {
            top.update(deltaSeconds);
        }
    }

    public void render(Graphics2D graphics, int width, int height) {
        Screen top = stack.peek();
        if (top == null) {
            return;
        }
        top.render(graphics, width, height);
    }

    public void applyPending() {
        if (pendingReplace != null) {
            if (!stack.isEmpty()) {
                stack.pop().onHide();
            }
            Screen next = pendingReplace.get();
            stack.push(next);
            next.onShow();
            pendingReplace = null;
        }
        if (pendingPop) {
            pendingPop = false;
            if (!stack.isEmpty()) {
                stack.pop().onHide();
            }
            if (!stack.isEmpty()) {
                stack.peek().onShow();
            }
        }
    }

    public boolean onKeyPressed(int keyCode) {
        Screen top = stack.peek();
        return top != null && top.onKeyPressed(keyCode);
    }

    public boolean onKeyReleased(int keyCode) {
        Screen top = stack.peek();
        return top != null && top.onKeyReleased(keyCode);
    }

    public boolean onMouseMoved(int x, int y) {
        Screen top = stack.peek();
        return top != null && top.onMouseMoved(x, y);
    }

    public boolean onMousePressed(int x, int y, int button) {
        Screen top = stack.peek();
        return top != null && top.onMousePressed(x, y, button);
    }

    public boolean onMouseReleased(int x, int y, int button) {
        Screen top = stack.peek();
        return top != null && top.onMouseReleased(x, y, button);
    }

    public boolean onMouseWheel(int rotation) {
        Screen top = stack.peek();
        return top != null && top.onMouseWheel(rotation);
    }

    public void onResize(int width, int height) {
        for (Screen screen : stack) {
            screen.onResize(width, height);
        }
    }
}
