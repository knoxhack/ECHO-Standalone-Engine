package dev.echo.engine.world;

import dev.echo.engine.api.ResourceId;
import dev.echo.engine.util.AtomicFiles;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class WorldStorage {
    private static final int MAGIC = 0x45434843;
    private static final int VERSION = 2;

    public int saveDirty(Path worldRoot, VoxelWorld world) throws IOException {
        Path chunks = worldRoot.resolve("chunks");
        Files.createDirectories(chunks);
        int count = 0;
        for (Chunk chunk : world.chunks()) {
            if (!chunk.dirty()) continue;
            AtomicFiles.replace(file(chunks, chunk.pos()), encode(chunk, world));
            chunk.markClean();
            count++;
        }
        return count;
    }

    public int saveAll(Path worldRoot, VoxelWorld world) throws IOException {
        Path chunks = worldRoot.resolve("chunks");
        Files.createDirectories(chunks);
        int count = 0;
        for (Chunk chunk : world.chunks()) {
            AtomicFiles.replace(file(chunks, chunk.pos()), encode(chunk, world));
            chunk.markClean();
            count++;
        }
        return count;
    }

    public int loadAll(Path worldRoot, VoxelWorld world) throws IOException {
        Path chunks = worldRoot.resolve("chunks");
        if (!Files.isDirectory(chunks)) return 0;
        List<Path> files;
        try (var stream = Files.list(chunks)) {
            files = stream.filter(p -> p.getFileName().toString().endsWith(".echc")).sorted().toList();
        }
        int count = 0;
        for (Path path : files) {
            Chunk chunk = decode(Files.readAllBytes(path), world);
            world.putChunkIfAbsent(chunk);
            count++;
        }
        return count;
    }

    private static Path file(Path root, ChunkPos pos) {
        return root.resolve("c." + pos.x() + "." + pos.z() + ".echc");
    }

    private static byte[] encode(Chunk chunk, VoxelWorld world) throws IOException {
        int[] runtimeBlocks = chunk.copyBlocks();
        LinkedHashMap<Integer, Integer> paletteIndex = new LinkedHashMap<>();
        for (int runtimeId : runtimeBlocks) paletteIndex.computeIfAbsent(runtimeId, ignored -> paletteIndex.size());
        ArrayList<String> palette = new ArrayList<>(paletteIndex.size());
        paletteIndex.keySet().forEach(id -> palette.add(world.blocks().byRuntimeId(id).id().toString()));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes); DataOutputStream out = new DataOutputStream(gzip)) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(chunk.pos().x());
            out.writeInt(chunk.pos().z());
            out.writeLong(chunk.version());
            out.writeInt(palette.size());
            for (String id : palette) out.writeUTF(id);
            out.writeInt(runtimeBlocks.length);
            for (int runtimeId : runtimeBlocks) out.writeInt(paletteIndex.get(runtimeId));
        }
        return bytes.toByteArray();
    }

    private static Chunk decode(byte[] bytes, VoxelWorld world) throws IOException {
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(bytes)))) {
            if (in.readInt() != MAGIC) throw new IOException("Invalid ECHO chunk magic");
            int version = in.readInt();
            if (version != VERSION) throw new IOException("Unsupported ECHO chunk version: " + version);
            Chunk chunk = new Chunk(new ChunkPos(in.readInt(), in.readInt()));
            long chunkVersion = in.readLong();
            int paletteSize = in.readInt();
            int[] runtimePalette = new int[paletteSize];
            int missing = world.blocks().runtimeId(ResourceId.parse("echo:missing_block"));
            for (int i = 0; i < paletteSize; i++) {
                ResourceId id = ResourceId.parse(in.readUTF());
                runtimePalette[i] = world.blocks().find(id).isPresent() ? world.blocks().runtimeId(id) : missing;
            }
            int length = in.readInt();
            if (length != Chunk.SIZE * Chunk.HEIGHT * Chunk.SIZE) throw new IOException("Invalid ECHO chunk length: " + length);
            int[] blocks = new int[length];
            for (int i = 0; i < length; i++) {
                int paletteId = in.readInt();
                if (paletteId < 0 || paletteId >= runtimePalette.length) throw new IOException("Invalid chunk palette id: " + paletteId);
                blocks[i] = runtimePalette[paletteId];
            }
            chunk.replaceBlocks(blocks, chunkVersion);
            return chunk;
        }
    }
}
