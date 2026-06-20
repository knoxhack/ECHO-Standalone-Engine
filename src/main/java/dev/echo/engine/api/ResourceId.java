package dev.echo.engine.api;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record ResourceId(String namespace, String path) implements Comparable<ResourceId> {
    private static final Pattern PART = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9_./-]+");

    public ResourceId {
        namespace = normalize(namespace, "echo");
        path = normalize(path, "missing");
        if (!PART.matcher(namespace).matches()) {
            throw new IllegalArgumentException("Invalid resource namespace: " + namespace);
        }
        if (!PATH.matcher(path).matches()) {
            throw new IllegalArgumentException("Invalid resource path: " + path);
        }
    }

    public static ResourceId parse(String value) {
        String text = Objects.requireNonNull(value, "value").trim().toLowerCase(Locale.ROOT);
        int split = text.indexOf(':');
        return split < 0 ? new ResourceId("echo", text) : new ResourceId(text.substring(0, split), text.substring(split + 1));
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toLowerCase(Locale.ROOT);
    }

    @Override public String toString() { return namespace + ":" + path; }
    @Override public int compareTo(ResourceId other) { return toString().compareTo(other.toString()); }
}
