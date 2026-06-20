package dev.echo.modules.ashfall;

import dev.echo.engine.api.graph.ContentGraphView;
import dev.echo.engine.api.graph.GraphNodeView;

/** Gameplay numbers come from canonical graph profile nodes, never a standalone fallback profile. */
record AshfallTuning(
        double hungerDrainPerSecond,
        double hydrationDrainPerSecond,
        double dehydrationDamage,
        double starvationDamage,
        double damageIntervalSeconds,
        double toxicExposurePerSecond,
        double passiveExposurePerSecond,
        double shelterRecoveryPerSecond,
        double criticalExposure,
        double criticalDamage,
        double warningExposure,
        double warningIntervalSeconds,
        int toxicSourceRadius,
        int shelterRadius,
        double stormCycleSeconds,
        double stormActiveSeconds,
        double stormExposureMultiplier
) {
    static AshfallTuning from(ContentGraphView graph) {
        GraphNodeView survival = graph.requireNode("echoashfallprotocol:survival/basic_needs");
        GraphNodeView hazard = graph.requireNode("echoashfallprotocol:hazard/toxic_ash");
        GraphNodeView weather = graph.requireNode("echoashfallprotocol:weather/ash_storm");
        GraphNodeView shelter = graph.requireNode("echoashfallprotocol:block/ash_campfire");
        return new AshfallTuning(
                survival.requireDecimal("hungerDrainPerSecond"),
                survival.requireDecimal("hydrationDrainPerSecond"),
                survival.requireDecimal("dehydrationDamage"),
                survival.requireDecimal("starvationDamage"),
                survival.requireDecimal("damageIntervalSeconds"),
                hazard.requireDecimal("exposurePerSecond"),
                hazard.requireDecimal("passiveExposurePerSecond"),
                hazard.requireDecimal("shelterRecoveryPerSecond"),
                hazard.requireDecimal("criticalExposure"),
                hazard.requireDecimal("criticalDamage"),
                hazard.requireDecimal("warningExposure"),
                hazard.requireDecimal("warningIntervalSeconds"),
                hazard.requireInteger("sourceRadius"),
                shelter.requireInteger("shelterRadius"),
                weather.requireDecimal("cycleSeconds"),
                weather.requireDecimal("activeSeconds"),
                weather.requireDecimal("exposureMultiplier")
        );
    }
}
