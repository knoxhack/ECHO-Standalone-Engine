package dev.echo.engine.game;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class EngineCliTest {
    private EngineCliTest() {}

    public static void run(Path packRoot, Path manifest) throws Exception {
        Path jar = packRoot.resolve("echo-standalone-engine-" + GameSession.ENGINE_VERSION + ".jar");
        if (!Files.isRegularFile(jar)) {
            throw new AssertionError("Engine JAR not found: " + jar);
        }

        runCommand(packRoot, manifest, jar, "--launcher-info");
        runCommand(packRoot, manifest, jar, "--verify");
        runCommand(packRoot, manifest, jar, "--repair");

        Path log = packRoot.resolve("last-launch-test.log");
        Files.deleteIfExists(log);
        runCommand(packRoot, manifest, jar, "--launcher-info", "--last-launch-log", log.toString());
        if (!Files.isRegularFile(log)) {
            throw new AssertionError("--last-launch-log did not create log file: " + log);
        }
        String logText = Files.readString(log);
        if (!logText.contains("ECHO Standalone Engine")) {
            throw new AssertionError("Log file missing expected engine header");
        }
        Files.deleteIfExists(log);
    }

    private static List<String> runCommand(Path packRoot, Path manifest, Path jar, String... extraArgs) throws Exception {
        List<String> command = new ArrayList<>(List.of(
                "java", "-Djava.awt.headless=true", "-jar", jar.toString(),
                "--pack-root", packRoot.toString(),
                "--manifest", manifest.toString()
        ));
        for (String arg : extraArgs) {
            command.add(arg);
        }
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.inheritIO();
        builder.redirectErrorStream(true);
        Process process = builder.start();
        List<String> lines;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            lines = reader.lines().collect(Collectors.toList());
        }
        int code = process.waitFor();
        if (code != 0) {
            throw new AssertionError("Engine CLI exited with code " + code + ":\n" + String.join("\n", lines));
        }
        return lines;
    }
}
