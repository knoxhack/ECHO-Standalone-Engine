package dev.echo.engine.game;

import java.nio.file.Path;

public record LaunchOptions(
        Path packRoot,
        Path manifest,
        Path saveRoot,
        boolean headlessSmoke,
        boolean strict,
        Mode mode,
        Path lastLaunchLog
) {
    public enum Mode {
        GAME,
        HEADLESS_SMOKE,
        VERIFY,
        REPAIR,
        LAUNCHER_INFO
    }

    public static LaunchOptions parse(String[] args) {
        Path packRoot = Path.of("dist");
        Path manifest = Path.of("pack.json");
        Path saveRoot = Path.of("saves");
        boolean headless = false;
        boolean strict = true;
        Mode mode = Mode.GAME;
        Path lastLaunchLog = null;

        String[] values = args == null ? new String[0] : args;
        for (int i = 0; i < values.length; i++) {
            switch (values[i]) {
                case "--pack-root" -> packRoot = Path.of(require(values, ++i, "--pack-root"));
                case "--manifest" -> manifest = Path.of(require(values, ++i, "--manifest"));
                case "--save-root" -> saveRoot = Path.of(require(values, ++i, "--save-root"));
                case "--headless-smoke" -> {
                    headless = true;
                    mode = Mode.HEADLESS_SMOKE;
                }
                case "--verify" -> mode = Mode.VERIFY;
                case "--repair" -> mode = Mode.REPAIR;
                case "--launcher-info" -> mode = Mode.LAUNCHER_INFO;
                case "--last-launch-log" -> lastLaunchLog = Path.of(require(values, ++i, "--last-launch-log"));
                case "--dev" -> strict = false;
                default -> throw new IllegalArgumentException("Unknown argument: " + values[i]);
            }
        }
        packRoot = packRoot.toAbsolutePath().normalize();
        Path manifestPath = manifest.isAbsolute() ? manifest : packRoot.resolve(manifest).normalize();
        if (lastLaunchLog != null) {
            lastLaunchLog = lastLaunchLog.toAbsolutePath().normalize();
        }
        return new LaunchOptions(
                packRoot,
                manifestPath,
                saveRoot.toAbsolutePath().normalize(),
                headless,
                strict,
                mode,
                lastLaunchLog
        );
    }

    private static String require(String[] values, int index, String option) {
        if (index >= values.length || values[index].isBlank()) {
            throw new IllegalArgumentException("Missing value after " + option);
        }
        return values[index];
    }
}
