package dev.echo.engine.api;

public record ModuleDependency(String id, String versionRange, boolean optional) {
    public ModuleDependency {
        id = id == null ? "" : id.trim().toLowerCase(java.util.Locale.ROOT);
        versionRange = versionRange == null || versionRange.isBlank() ? "*" : versionRange.trim();
        if (id.isBlank()) throw new IllegalArgumentException("dependency id must not be blank");
    }
}
