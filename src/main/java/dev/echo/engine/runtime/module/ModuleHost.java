package dev.echo.engine.runtime.module;

import dev.echo.engine.api.EchoModule;
import dev.echo.engine.api.ModuleDescriptor;
import dev.echo.engine.api.ModuleLogger;
import dev.echo.engine.runtime.adaptercore.AdapterCoreAudit;
import dev.echo.engine.runtime.adaptercore.AdapterCoreRuntime;
import dev.echo.engine.runtime.content.CanonicalContentMap;
import dev.echo.engine.runtime.content.ContentGraphMerger;
import dev.echo.engine.runtime.content.ModuleContentBundle;
import dev.echo.engine.runtime.content.ModuleContentGraphLoader;
import dev.echo.engine.runtime.registry.DefaultContentRegistries;
import dev.echo.engine.util.Hashing;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Strict installed-module boot pipeline:
 * verify artifacts -> parse descriptors -> load canonical graphs -> merge/validate -> materialize through
 * AdapterCore -> execute trusted entrypoints through module-scoped AdapterCore sessions -> freeze runtime IDs.
 */
public final class ModuleHost implements AutoCloseable {
    private static final String DESCRIPTOR_PATH = "META-INF/echo.mod.json";

    private final Path packRoot;
    private final PackManifest manifest;
    private final DefaultContentRegistries registries = new DefaultContentRegistries();
    private final DefaultEventBus events = new DefaultEventBus();
    private final DefaultServiceRegistry services = new DefaultServiceRegistry();
    private final LinkedHashMap<String, DefaultModuleState> states = new LinkedHashMap<>();
    private final ArrayList<LoadedModule> loaded = new ArrayList<>();
    private final LinkedHashMap<String, String> moduleFingerprints = new LinkedHashMap<>();

    private CanonicalContentMap contentMap;
    private AdapterCoreRuntime adapterCore;

    public ModuleHost(Path packRoot, PackManifest manifest) {
        this.packRoot = packRoot.toAbsolutePath().normalize();
        this.manifest = java.util.Objects.requireNonNull(manifest, "manifest");
    }

    public void load() throws Exception {
        List<ModuleCandidate> ordered = ModuleGraph.sort(discover());
        List<ModuleContentBundle> bundles = ordered.stream().map(ModuleCandidate::contentBundle).toList();
        contentMap = new ContentGraphMerger().merge(bundles, manifest.requireCrossRuntimeParity());
        adapterCore = new AdapterCoreRuntime(contentMap, registries, services, events);

        try {
            for (ModuleCandidate candidate : ordered) adapterCore.materialize(candidate.descriptor());
            for (ModuleCandidate candidate : ordered) loadEntrypoint(candidate);
            AdapterCoreAudit audit = adapterCore.audit();
            if (!audit.ready()) {
                throw new IllegalStateException("AdapterCore rejected " + audit.rejected() + " runtime mutation(s)");
            }
            adapterCore.freeze();
            if (registries.runtimeWorldGenerators().values().isEmpty()) {
                throw new IllegalStateException("No graph-backed world generator was bound by pack " + manifest.id());
            }
        } catch (Exception failure) {
            closeLoadedModules();
            throw failure;
        }
    }

    private List<ModuleCandidate> discover() throws IOException {
        ModuleContentGraphLoader graphLoader = new ModuleContentGraphLoader();
        ArrayList<ModuleCandidate> result = new ArrayList<>();
        for (PackModule module : manifest.modules()) {
            Path modulePath = packRoot.resolve(module.file()).normalize();
            if (!modulePath.startsWith(packRoot)) {
                throw new SecurityException("Module path escapes pack root: " + module.file());
            }
            if (!Files.isRegularFile(modulePath)) {
                if (module.required()) throw new IOException("Required module missing: " + modulePath);
                continue;
            }
            long actualSize = Files.size(modulePath);
            if (manifest.strictArtifacts() && module.required()
                    && (module.sha256().isBlank() || module.size() <= 0L)) {
                throw new SecurityException(
                        "Strict artifact identity is incomplete for " + module.id());
            }
            if (module.size() > 0L && module.size() != actualSize) {
                throw new SecurityException(
                        "Size mismatch for " + module.id() + ": expected " + module.size() + " got " + actualSize
                );
            }
            String actualHash = Hashing.sha256(modulePath);
            if (!module.sha256().isBlank() && !actualHash.equalsIgnoreCase(module.sha256())) {
                throw new SecurityException(
                        "SHA-256 mismatch for " + module.id() + ": expected " + module.sha256() + " got " + actualHash
                );
            }

            ModuleDescriptor descriptor;
            try (JarFile jar = new JarFile(modulePath.toFile())) {
                JarEntry entry = jar.getJarEntry(DESCRIPTOR_PATH);
                if (entry == null) throw new IOException("Missing " + DESCRIPTOR_PATH + " in " + modulePath);
                try (InputStream input = jar.getInputStream(entry)) {
                    descriptor = ModuleDescriptorCodec.parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
            if (!descriptor.id().equals(module.id())) {
                throw new IllegalArgumentException(
                        "Manifest id " + module.id() + " != descriptor id " + descriptor.id()
                );
            }
            if (!module.version().equals("*") && !module.version().equals(descriptor.version())) {
                throw new IllegalArgumentException(
                        "Version mismatch for " + module.id() + ": manifest=" + module.version()
                                + " descriptor=" + descriptor.version()
                );
            }
            ModuleContentBundle graph = graphLoader.load(modulePath, descriptor.id(), manifest.strictContentGraph());
            moduleFingerprints.put(descriptor.id(), actualHash);
            result.add(new ModuleCandidate(module, modulePath, descriptor, graph));
        }
        return List.copyOf(result);
    }

    private void loadEntrypoint(ModuleCandidate candidate) throws Exception {
        ModuleDescriptor descriptor = candidate.descriptor();
        EchoModule instance = null;
        URLClassLoader classLoader = null;
        if (descriptor.executable()) {
            if (!List.of("official", "developer").contains(descriptor.trust())
                    || !List.of("official", "developer").contains(candidate.packModule().trust())) {
                throw new SecurityException(
                        "Executable module " + descriptor.id() + " is not trusted by both descriptor and pack"
                );
            }
            classLoader = new URLClassLoader(
                    new java.net.URL[]{candidate.path().toUri().toURL()},
                    EchoModule.class.getClassLoader()
            );
            Class<?> type = Class.forName(descriptor.entrypoint(), true, classLoader);
            Object value = type.getDeclaredConstructor().newInstance();
            if (!(value instanceof EchoModule module)) {
                throw new IllegalArgumentException(descriptor.entrypoint() + " does not implement EchoModule");
            }
            instance = module;
            DefaultModuleState state = states.computeIfAbsent(descriptor.id(), ignored -> new DefaultModuleState());
            instance.onLoad(new DefaultModuleContext(
                    descriptor,
                    contentMap.moduleView(descriptor.id()),
                    adapterCore.session(descriptor),
                    services,
                    state,
                    logger(descriptor.id()),
                    packRoot,
                    candidate.path()
            ));
        }
        loaded.add(new LoadedModule(descriptor, candidate.path(), instance, classLoader));
    }

    private static ModuleLogger logger(String id) {
        return new ModuleLogger() {
            @Override
            public void info(String message) {
                System.out.println("[module:" + id + "] " + message);
            }

            @Override
            public void warn(String message) {
                System.err.println("[module:" + id + "] WARN " + message);
            }

            @Override
            public void error(String message, Throwable failure) {
                System.err.println("[module:" + id + "] ERROR " + message);
                if (failure != null) failure.printStackTrace(System.err);
            }
        };
    }

    public DefaultContentRegistries registries() {
        return registries;
    }

    public DefaultEventBus events() {
        return events;
    }

    public DefaultServiceRegistry services() {
        return services;
    }

    public PackManifest manifest() {
        return manifest;
    }

    public CanonicalContentMap contentMap() {
        if (contentMap == null) throw new IllegalStateException("module host not loaded");
        return contentMap;
    }

    public AdapterCoreRuntime adapterCore() {
        if (adapterCore == null) throw new IllegalStateException("module host not loaded");
        return adapterCore;
    }

    public List<LoadedModule> loadedModules() {
        return List.copyOf(loaded);
    }

    public Map<String, String> moduleFingerprints() {
        return Map.copyOf(moduleFingerprints);
    }

    public Map<String, DefaultModuleState> states() {
        return Map.copyOf(states);
    }

    public DefaultModuleState state(String moduleId) {
        return states.computeIfAbsent(moduleId, ignored -> new DefaultModuleState());
    }

    public void restoreStates(Map<String, Map<String, String>> values) {
        if (values == null) return;
        values.forEach((id, state) -> states.computeIfAbsent(id, ignored -> new DefaultModuleState()).replace(state));
    }

    @Override
    public void close() {
        RuntimeException failure = closeLoadedModules();
        if (adapterCore != null) adapterCore.close();
        if (failure != null) throw failure;
    }

    private RuntimeException closeLoadedModules() {
        RuntimeException failure = null;
        for (int index = loaded.size() - 1; index >= 0; index--) {
            try {
                loaded.get(index).close();
            } catch (RuntimeException exception) {
                if (failure == null) failure = exception;
                else failure.addSuppressed(exception);
            }
        }
        loaded.clear();
        return failure;
    }
}
