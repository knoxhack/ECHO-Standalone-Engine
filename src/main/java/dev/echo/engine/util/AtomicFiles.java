package dev.echo.engine.util;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class AtomicFiles {
    private AtomicFiles() { }
    public static void replace(Path target, byte[] bytes) throws IOException {
        Path normalized = target.toAbsolutePath().normalize();
        Files.createDirectories(normalized.getParent());
        Path temp = Files.createTempFile(normalized.getParent(), normalized.getFileName().toString(), ".tmp");
        try {
            Files.write(temp, bytes);
            try { Files.move(temp, normalized, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temp, normalized, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}
