package dev.echo.modules.ashfall;

import dev.echo.engine.api.MutableChunk;
import dev.echo.engine.api.ResourceId;
import dev.echo.engine.api.WorldGenerator;
import dev.echo.engine.api.graph.GraphNodeView;

/** Ashlands algorithm parameterized entirely by the canonical worldgen node and exported block identities. */
final class AshfallWorldGenerator implements WorldGenerator {
    private static final ResourceId AIR = ResourceId.parse("echo:air");

    private final AshfallRuntimeIds ids;
    private final int baseHeight;
    private final double broadAmplitude;
    private final double detailAmplitude;
    private final int minHeight;
    private final int maxHeight;
    private final double broadScale;
    private final double detailScale;
    private final double toxicAshThreshold;
    private final double debrisThreshold;

    AshfallWorldGenerator(AshfallRuntimeIds ids, GraphNodeView worldgenNode) {
        this.ids = ids;
        baseHeight = worldgenNode.requireInteger("baseHeight");
        broadAmplitude = worldgenNode.requireDecimal("broadAmplitude");
        detailAmplitude = worldgenNode.requireDecimal("detailAmplitude");
        minHeight = worldgenNode.requireInteger("minHeight");
        maxHeight = worldgenNode.requireInteger("maxHeight");
        broadScale = worldgenNode.requireDecimal("broadScale");
        detailScale = worldgenNode.requireDecimal("detailScale");
        toxicAshThreshold = worldgenNode.requireDecimal("toxicAshThreshold");
        debrisThreshold = worldgenNode.requireDecimal("debrisThreshold");
    }

    @Override
    public void generate(MutableChunk chunk, long seed, int chunkX, int chunkZ) {
        int[][] surface = new int[chunk.size()][chunk.size()];
        for (int z = 0; z < chunk.size(); z++) {
            for (int x = 0; x < chunk.size(); x++) {
                int worldX = chunkX * chunk.size() + x;
                int worldZ = chunkZ * chunk.size() + z;
                double broad = fractal(seed, worldX * broadScale, worldZ * broadScale, 4);
                double detail = fractal(seed + 91, worldX * detailScale, worldZ * detailScale, 3);
                int height = clamp(
                        (int) Math.round(baseHeight + broad * broadAmplitude + detail * detailAmplitude),
                        minHeight,
                        maxHeight
                );
                surface[x][z] = height;
                for (int y = 0; y < height; y++) {
                    chunk.setBlock(x, y, z, y >= height - 2 ? ids.ashSoil() : ids.basalt());
                }
                double hazard = fractal(seed + 701, worldX * 0.11, worldZ * 0.11, 2);
                if (hazard > toxicAshThreshold) chunk.setBlock(x, height, z, ids.toxicAsh());
                else if (hash(seed + 33, worldX, worldZ) > debrisThreshold) chunk.setBlock(x, height, z, ids.debris());
            }
        }
        if (chunkX == 0 && chunkZ == 0) buildCrashSite(chunk, surface);
    }

    private void buildCrashSite(MutableChunk chunk, int[][] surface) {
        flatten(chunk, surface, 1, 1, 14, 14);
        int y = surface[7][7] + 1;
        platform(chunk, y - 1);
        chunk.setBlock(3, y, 3, ids.terminal());
        chunk.setBlock(6, y, 4, ids.shelter());
        chunk.setBlock(10, y, 4, ids.powerNode());
        chunk.setBlock(13, y, 5, ids.cache());
        chunk.setBlock(5, y, 10, ids.rainCollector());
        chunk.setBlock(9, y, 10, ids.waterPurifier());
        chunk.setBlock(11, y, 10, ids.scrapPress());
        chunk.setBlock(13, y, 10, ids.radiationCleanser());
        for (int x = 1; x < 15; x++) {
            chunk.setBlock(x, y - 1, 1, ids.basalt());
            chunk.setBlock(x, y - 1, 14, ids.basalt());
        }
        for (int z = 1; z < 15; z++) {
            chunk.setBlock(1, y - 1, z, ids.basalt());
            chunk.setBlock(14, y - 1, z, ids.basalt());
        }
    }

    private void flatten(MutableChunk chunk, int[][] surface, int minX, int minZ, int maxX, int maxZ) {
        int target = surface[7][7];
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                for (int y = 0; y < target - 2; y++) chunk.setBlock(x, y, z, ids.basalt());
                for (int y = Math.max(0, target - 2); y < target; y++) chunk.setBlock(x, y, z, ids.ashSoil());
                for (int y = target; y < chunk.height(); y++) chunk.setBlock(x, y, z, AIR);
                surface[x][z] = target;
            }
        }
    }

    private void platform(MutableChunk chunk, int y) {
        for (int z = 1; z < 15; z++) {
            for (int x = 1; x < 15; x++) chunk.setBlock(x, y, z, ids.ashSoil());
        }
    }

    private static double fractal(long seed, double x, double z, int octaves) {
        double result = 0.0;
        double amplitude = 1.0;
        double total = 0.0;
        double frequency = 1.0;
        for (int index = 0; index < octaves; index++) {
            result += value(seed + index * 7919L, x * frequency, z * frequency) * amplitude;
            total += amplitude;
            frequency *= 2.0;
            amplitude *= 0.5;
        }
        return result / total;
    }

    private static double value(long seed, double x, double z) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        double tx = smooth(x - x0);
        double tz = smooth(z - z0);
        return lerp(
                lerp(hash(seed, x0, z0) * 2 - 1, hash(seed, x0 + 1, z0) * 2 - 1, tx),
                lerp(hash(seed, x0, z0 + 1) * 2 - 1, hash(seed, x0 + 1, z0 + 1) * 2 - 1, tx),
                tz
        );
    }

    private static double hash(long seed, int x, int z) {
        long value = seed + x * 341873128712L + z * 132897987541L;
        value = (value ^ value >>> 13) * 1274126177L;
        value ^= value >>> 16;
        return (value & 0xFFFFFF) / (double) 0xFFFFFF;
    }

    private static double smooth(double value) {
        return value * value * (3.0 - 2.0 * value);
    }

    private static double lerp(double left, double right, double amount) {
        return left + (right - left) * amount;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
