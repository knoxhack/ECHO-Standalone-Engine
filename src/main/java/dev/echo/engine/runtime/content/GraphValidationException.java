package dev.echo.engine.runtime.content;

import java.util.List;

@SuppressWarnings("serial")
public final class GraphValidationException extends IllegalArgumentException {
    private final List<String> diagnostics;

    public GraphValidationException(String message, List<String> diagnostics) {
        super(message + (diagnostics == null || diagnostics.isEmpty() ? "" : ": " + String.join("; ", diagnostics)));
        this.diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public List<String> diagnostics() {
        return diagnostics;
    }
}
