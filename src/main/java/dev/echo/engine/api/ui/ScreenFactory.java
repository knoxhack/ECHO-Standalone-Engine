package dev.echo.engine.api.ui;

import dev.echo.engine.ui.Screen;
import dev.echo.engine.ui.ScreenManager;

/**
 * Service interface modules can publish against {@code UI_SCREEN} graph nodes.
 * The Engine's ScreenCore runtime calls this factory when a screen is requested by ID.
 */
@FunctionalInterface
public interface ScreenFactory {
    Screen create(ScreenManager manager);
}
