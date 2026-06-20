package dev.echo.engine.runtime.content;

import java.util.List;
import java.util.Map;

public record CrossRuntimeParityReport(
        boolean ready,
        int canonicalNodeCount,
        Map<String, Integer> mappedNodeCounts,
        List<String> blockers
) {
    public CrossRuntimeParityReport {
        mappedNodeCounts = mappedNodeCounts == null ? Map.of() : Map.copyOf(mappedNodeCounts);
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
    }
}
