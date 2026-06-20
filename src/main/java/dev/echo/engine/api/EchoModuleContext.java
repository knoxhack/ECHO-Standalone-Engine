package dev.echo.engine.api;

import dev.echo.engine.api.adapter.AdapterCoreSession;
import dev.echo.engine.api.graph.ContentGraphView;

import java.nio.file.Path;

/** Runtime context exposed to trusted ECHO module entrypoints. */
public interface EchoModuleContext {
    ModuleDescriptor descriptor();

    /** Canonical content identity and configuration for this module and the merged pack graph. */
    ContentGraphView contentGraph();

    /** The only supported mutation bridge into the standalone runtime. */
    AdapterCoreSession adapterCore();

    /** Read-only lookup of services that AdapterCore has already accepted. */
    ServiceRegistry services();

    ModuleState state();

    ModuleLogger logger();

    Path packRoot();

    Path modulePath();
}
