package dev.echo.engine.runtime.module;

import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;

/** Exact installed module artifact requirement from an ECHO pack manifest. */
public record PackModule(
        String id,
        String version,
        String file,
        String sha256,
        long size,
        boolean required,
        String trust,
        String artifactFamily
) {
    private static final Pattern MODULE_ID = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern SHA_256 = Pattern.compile("[a-f0-9]{64}");

    public PackModule {
        id = normalize(id);
        version = version == null || version.isBlank() ? "*" : version.trim();
        file = file == null ? "" : file.trim().replace('\\', '/');
        sha256 = sha256 == null ? "" : sha256.trim().toLowerCase(Locale.ROOT);
        size = Math.max(0L, size);
        trust = trust == null || trust.isBlank()
                ? "data-only"
                : trust.trim().toLowerCase(Locale.ROOT);
        artifactFamily = artifactFamily == null || artifactFamily.isBlank()
                ? "standalone"
                : artifactFamily.trim().toLowerCase(Locale.ROOT);
        if (!MODULE_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("invalid pack module id: " + id);
        }
        if (file.isBlank()) {
            throw new IllegalArgumentException("pack module file required for " + id);
        }
        Path normalizedPath = Path.of(file).normalize();
        if (normalizedPath.isAbsolute()
                || normalizedPath.startsWith("..")
                || file.contains("://")) {
            throw new IllegalArgumentException("unsafe module artifact path for " + id + ": " + file);
        }
        if (!sha256.isBlank() && !SHA_256.matcher(sha256).matches()) {
            throw new IllegalArgumentException("invalid SHA-256 for " + id);
        }
        if (!artifactFamily.equals("standalone")) {
            throw new IllegalArgumentException(
                    "Standalone runtime cannot load artifact family "
                            + artifactFamily + " for " + id);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
