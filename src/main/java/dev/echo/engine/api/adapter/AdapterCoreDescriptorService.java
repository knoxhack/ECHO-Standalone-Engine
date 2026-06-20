package dev.echo.engine.api.adapter;

/** Runtime-visible identity published by the installed echoadaptercore module. */
public interface AdapterCoreDescriptorService {
    String contractId();

    String runtimeTarget();

    String graphFingerprint();
}
