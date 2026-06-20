package dev.echo.engine.game;

import java.io.IOException;
import java.util.List;

/** Recoverable save refusal: the world is left untouched and can be reopened with the recorded content set. */
public final class SaveCompatibilityException extends IOException {
    private static final long serialVersionUID = 1L;
    private final transient List<String> blockers;

    public SaveCompatibilityException(String message, List<String> blockers) {
        super(message + (blockers == null || blockers.isEmpty() ? "" : ": " + String.join("; ", blockers)));
        this.blockers = blockers == null ? List.of() : List.copyOf(blockers);
    }

    public List<String> blockers() {
        return blockers;
    }
}
