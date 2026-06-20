package dev.echo.engine.api.adapter;

import java.time.Instant;
import java.util.Map;

/** Auditable AdapterCore result for one requested runtime mutation. */
public record MutationReceipt(
        long sequence,
        String mutationId,
        String moduleId,
        String graphNodeId,
        String domain,
        MutationStatus status,
        String message,
        Instant recordedAt,
        Map<String, String> details
) {
    public MutationReceipt {
        mutationId = mutationId == null ? "" : mutationId;
        moduleId = moduleId == null ? "" : moduleId;
        graphNodeId = graphNodeId == null ? "" : graphNodeId;
        domain = domain == null ? "" : domain;
        status = status == null ? MutationStatus.REJECTED : status;
        message = message == null ? "" : message;
        recordedAt = recordedAt == null ? Instant.EPOCH : recordedAt;
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    public boolean accepted() {
        return status == MutationStatus.ACCEPTED;
    }
}
