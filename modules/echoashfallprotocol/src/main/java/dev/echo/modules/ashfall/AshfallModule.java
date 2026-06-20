package dev.echo.modules.ashfall;

import dev.echo.engine.api.EchoModule;
import dev.echo.engine.api.EchoModuleContext;
import dev.echo.engine.api.adapter.MutationReceipt;
import dev.echo.engine.api.graph.GraphNodeView;

/** Executable Ashfall adapter that binds graph-defined behavior through AdapterCore. */
public final class AshfallModule implements EchoModule {
    private static final String WORLDGEN_NODE = "echoashfallprotocol:worldgen/ashlands";
    private static final String GAMEPLAY_NODE = "echoashfallprotocol:system/ashfall_runtime";

    @Override
    public void onLoad(EchoModuleContext context) {
        AshfallRuntimeIds ids = AshfallRuntimeIds.from(context.contentGraph());
        AshfallTuning tuning = AshfallTuning.from(context.contentGraph());
        GraphNodeView worldgen = context.contentGraph().requireNode(WORLDGEN_NODE);

        MutationReceipt worldgenReceipt = context.adapterCore().bindWorldGenerator(
                WORLDGEN_NODE,
                worldgen.requireInteger("priority"),
                new AshfallWorldGenerator(ids, worldgen)
        );
        requireAccepted(worldgenReceipt);

        MutationReceipt gameplayReceipt = context.adapterCore().bindGameExtension(
                GAMEPLAY_NODE,
                new AshfallGameExtension(context.contentGraph(), ids, tuning)
        );
        requireAccepted(gameplayReceipt);

        context.logger().info(
                "Ashfall activated from canonical Content Graph; nodes="
                        + context.contentGraph().totalNodeCount()
                        + " edges=" + context.contentGraph().totalEdgeCount()
                        + " modules=" + context.contentGraph().totalModuleCount()
                        + " fingerprint=" + context.contentGraph().fingerprint()
        );
    }

    private static void requireAccepted(MutationReceipt receipt) {
        if (!receipt.accepted()) {
            throw new IllegalStateException(
                    "AdapterCore rejected " + receipt.mutationId() + " for " + receipt.graphNodeId()
                            + ": " + receipt.message()
            );
        }
    }
}
