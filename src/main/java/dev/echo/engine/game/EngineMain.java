package dev.echo.engine.game;

import dev.echo.engine.util.TeeOutputStream;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;

public final class EngineMain {
    private EngineMain() {}

    public static void main(String[] args) {
        LaunchOptions options;
        try {
            options = LaunchOptions.parse(args);
        } catch (Throwable failure) {
            failure.printStackTrace(System.err);
            System.exit(1);
            return;
        }

        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        OutputStream logFile = null;
        try {
            if (options.lastLaunchLog() != null) {
                Files.createDirectories(options.lastLaunchLog().getParent());
                logFile = Files.newOutputStream(options.lastLaunchLog());
                System.setOut(new PrintStream(new TeeOutputStream(originalOut, logFile), true));
                System.setErr(new PrintStream(new TeeOutputStream(originalErr, logFile), true));
            }

            System.out.println("ECHO Standalone Engine " + GameSession.ENGINE_VERSION);
            System.out.println("packRoot=" + options.packRoot());
            System.out.println("manifest=" + options.manifest());

            switch (options.mode()) {
                case HEADLESS_SMOKE -> HeadlessSmoke.run(options);
                case VERIFY -> VerifyCommand.run(options);
                case REPAIR -> RepairCommand.run(options);
                case LAUNCHER_INFO -> LauncherInfo.print(options);
                case GAME -> {
                    try (GameClient client = new GameClient(options)) {
                        client.run();
                    }
                }
            }
        } catch (Throwable failure) {
            failure.printStackTrace(System.err);
            System.exit(1);
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
            if (logFile != null) {
                try {
                    logFile.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}
