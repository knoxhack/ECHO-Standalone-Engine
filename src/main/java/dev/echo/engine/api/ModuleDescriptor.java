package dev.echo.engine.api;

import java.util.List;
import java.util.Set;

/** Normalized subset of META-INF/echo.mod.json used by the standalone host. */
public record ModuleDescriptor(
        String schemaVersion,
        String id,
        String name,
        String version,
        String entrypoint,
        String trust,
        boolean standalone,
        boolean official,
        List<ModuleDependency> dependencies,
        Set<String> permissions,
        Set<String> contentNamespaces,
        List<String> classPath
) {
    public ModuleDescriptor {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank()
                ? "echo.module.descriptor.v1"
                : schemaVersion.trim();
        id = normalize(id);
        name = name == null || name.isBlank() ? id : name.trim();
        version = version == null || version.isBlank() ? "0.0.0" : version.trim();
        entrypoint = entrypoint == null ? "" : entrypoint.trim();
        trust = trust == null || trust.isBlank()
                ? (official ? "official" : "data-only")
                : trust.trim().toLowerCase(java.util.Locale.ROOT);
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        contentNamespaces = contentNamespaces == null || contentNamespaces.isEmpty()
                ? Set.of(id)
                : contentNamespaces.stream().map(ModuleDescriptor::normalize).collect(java.util.stream.Collectors.toUnmodifiableSet());
        classPath = classPath == null || classPath.isEmpty() ? List.of(".") : List.copyOf(classPath);
        if (id.isBlank()) throw new IllegalArgumentException("module id must not be blank");
        if (!standalone) throw new IllegalArgumentException("module " + id + " does not permit standalone execution");
    }

    public boolean executable() {
        return !entrypoint.isBlank();
    }

    public boolean canMutate(String permission) {
        return permissions.contains("*") || permissions.contains(permission);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
