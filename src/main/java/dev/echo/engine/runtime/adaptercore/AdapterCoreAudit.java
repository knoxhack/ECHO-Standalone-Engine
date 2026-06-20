package dev.echo.engine.runtime.adaptercore;

import java.util.Map;

public record AdapterCoreAudit(
        boolean ready,
        int accepted,
        int rejected,
        int revoked,
        Map<String, Integer> acceptedByDomain
) {
    public AdapterCoreAudit {
        acceptedByDomain = acceptedByDomain == null ? Map.of() : Map.copyOf(acceptedByDomain);
    }
}
