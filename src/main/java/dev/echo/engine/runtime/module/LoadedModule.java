package dev.echo.engine.runtime.module;

import dev.echo.engine.api.EchoModule;
import dev.echo.engine.api.ModuleDescriptor;

import java.net.URLClassLoader;
import java.nio.file.Path;

public record LoadedModule(
        ModuleDescriptor descriptor,
        Path path,
        EchoModule instance,
        URLClassLoader classLoader
) implements AutoCloseable {
    @Override
    public void close() {
        RuntimeException failure = null;
        try {
            if (instance != null) instance.onUnload();
        } catch (Exception exception) {
            failure = new IllegalStateException("Module unload failed: " + descriptor.id(), exception);
        }
        try {
            if (classLoader != null) classLoader.close();
        } catch (Exception exception) {
            RuntimeException wrapped = new IllegalStateException("Module class loader close failed: " + descriptor.id(), exception);
            if (failure == null) failure = wrapped;
            else failure.addSuppressed(wrapped);
        }
        if (failure != null) throw failure;
    }
}
