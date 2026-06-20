package dev.echo.engine.runtime.module;

import dev.echo.engine.api.ModuleDescriptor;
import dev.echo.engine.runtime.content.ModuleContentBundle;

import java.nio.file.Path;

record ModuleCandidate(
        PackModule packModule,
        Path path,
        ModuleDescriptor descriptor,
        ModuleContentBundle contentBundle
) {
}
