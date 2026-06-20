package dev.echo.modules.ashfall;

import dev.echo.engine.api.ResourceId;
import dev.echo.engine.api.graph.ContentGraphView;

/** Runtime identities are resolved exclusively from the standalone export plan. */
record AshfallRuntimeIds(
        ResourceId basalt,
        ResourceId ashSoil,
        ResourceId toxicAsh,
        ResourceId debris,
        ResourceId shelter,
        ResourceId terminal,
        ResourceId cache,
        ResourceId powerNode,
        ResourceId rainCollector,
        ResourceId waterPurifier,
        ResourceId scrapPress,
        ResourceId radiationCleanser,
        ResourceId cleanWater,
        ResourceId dirtyWater,
        ResourceId ration,
        ResourceId scrapMetal,
        ResourceId scrapWire,
        ResourceId filter,
        ResourceId powerCell,
        ResourceId compressedScrap,
        ResourceId scanner
) {
    static AshfallRuntimeIds from(ContentGraphView graph) {
        return new AshfallRuntimeIds(
                id(graph, "echoashfallprotocol:block/scorched_ash"),
                id(graph, "echoashfallprotocol:block/ash_soil"),
                id(graph, "echoashfallprotocol:block/fallout_dust"),
                id(graph, "echoashfallprotocol:block/rusted_metal_debris"),
                id(graph, "echoashfallprotocol:block/ash_campfire"),
                id(graph, "echoterminal:block/echo_terminal"),
                id(graph, "echoashfallprotocol:block/echo_cache"),
                id(graph, "echoashfallprotocol:block/power_node"),
                id(graph, "echoashfallprotocol:block/rain_collector"),
                id(graph, "echoashfallprotocol:block/water_purifier"),
                id(graph, "echoashfallprotocol:block/scrap_press"),
                id(graph, "echoashfallprotocol:block/radiation_cleanser"),
                id(graph, "echoashfallprotocol:item/clean_water_bottle"),
                id(graph, "echoashfallprotocol:item/dirty_water_bottle"),
                id(graph, "echoashfallprotocol:item/emergency_ration"),
                id(graph, "echomaterialcore:item/scrap_metal"),
                id(graph, "echomaterialcore:item/scrap_wire"),
                id(graph, "echoashfallprotocol:item/filter_cartridge_basic"),
                id(graph, "echoashfallprotocol:item/power_cell"),
                id(graph, "echoashfallprotocol:item/compressed_scrap"),
                id(graph, "echoashfallprotocol:item/portable_signal_scanner")
        );
    }

    private static ResourceId id(ContentGraphView graph, String nodeId) {
        return ResourceId.parse(graph.requireRuntimeId(nodeId));
    }
}
