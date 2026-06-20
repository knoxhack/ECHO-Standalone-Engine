package dev.echo.engine.runtime.module;

import dev.echo.engine.api.EchoModuleContext;
import dev.echo.engine.api.ModuleDescriptor;
import dev.echo.engine.api.ModuleLogger;
import dev.echo.engine.api.ModuleState;
import dev.echo.engine.api.ServiceRegistry;
import dev.echo.engine.api.adapter.AdapterCoreSession;
import dev.echo.engine.api.graph.ContentGraphView;

import java.nio.file.Path;

record DefaultModuleContext(
        ModuleDescriptor descriptor,
        ContentGraphView contentGraph,
        AdapterCoreSession adapterCore,
        ServiceRegistry services,
        ModuleState state,
        ModuleLogger logger,
        Path packRoot,
        Path modulePath
) implements EchoModuleContext {
}
