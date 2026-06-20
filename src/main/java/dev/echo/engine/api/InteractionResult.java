package dev.echo.engine.api;

public record InteractionResult(boolean handled, String message) {
    public static InteractionResult pass() { return new InteractionResult(false, ""); }
    public static InteractionResult handled(String message) { return new InteractionResult(true, message == null ? "" : message); }
}
