package dev.echo.engine.api.graph;

import java.util.List;
import java.util.Optional;

/**
 * Read-only view over the canonical merged ECHO Content Graph.
 * Modules use this view for identity and configuration; they do not register parallel content definitions.
 */
public interface ContentGraphView {
    String moduleId();

    String fingerprint();

    int totalModuleCount();

    int totalNodeCount();

    int totalEdgeCount();

    Optional<GraphNodeView> node(String nodeId);

    default GraphNodeView requireNode(String nodeId) {
        return node(nodeId).orElseThrow(() -> new IllegalArgumentException("Unknown graph node: " + nodeId));
    }

    List<GraphNodeView> nodesByKind(String kind);

    List<GraphEdgeView> outgoing(String nodeId, String edgeType);

    Optional<String> runtimeId(String nodeId);

    default String requireRuntimeId(String nodeId) {
        return runtimeId(nodeId).orElseThrow(
                () -> new IllegalArgumentException("No standalone export binding for graph node: " + nodeId)
        );
    }
}
