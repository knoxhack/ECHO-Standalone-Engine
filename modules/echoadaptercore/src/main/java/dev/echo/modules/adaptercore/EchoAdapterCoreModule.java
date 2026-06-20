package dev.echo.modules.adaptercore;

import dev.echo.engine.api.EchoModule;
import dev.echo.engine.api.EchoModuleContext;
import dev.echo.engine.api.adapter.AdapterCoreDescriptorService;
import dev.echo.engine.api.adapter.MutationReceipt;

/** Installed AdapterCore module identity. The host implementation remains runtime-owned. */
public final class EchoAdapterCoreModule implements EchoModule {
    @Override
    public void onLoad(EchoModuleContext context) {
        AdapterCoreDescriptorService service = new AdapterCoreDescriptorService() {
            @Override
            public String contractId() {
                return "echo.adaptercore.runtime.v1";
            }

            @Override
            public String runtimeTarget() {
                return "echo_runtime_standalone";
            }

            @Override
            public String graphFingerprint() {
                return context.contentGraph().fingerprint();
            }
        };
        MutationReceipt receipt = context.adapterCore().publishService(
                "echoadaptercore:service/runtime_bridge",
                AdapterCoreDescriptorService.class,
                service
        );
        if (!receipt.accepted()) throw new IllegalStateException("AdapterCore service rejected: " + receipt.message());
        context.logger().info(
                "AdapterCore canonical bridge active; graphNodes=" + context.contentGraph().totalNodeCount()
                        + " fingerprint=" + context.contentGraph().fingerprint()
        );
    }
}
