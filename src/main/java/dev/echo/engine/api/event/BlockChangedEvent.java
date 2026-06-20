package dev.echo.engine.api.event;

import dev.echo.engine.api.ResourceId;

public record BlockChangedEvent(int x, int y, int z, ResourceId previous, ResourceId current) { }
