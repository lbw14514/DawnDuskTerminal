package com.dawnduskterminal.debug;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.registry.ModDimensions;
import com.dawnduskterminal.world.ChronoLine;
import com.dawnduskterminal.world.ChronoLineState;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;

/**
 * 专用服地形指纹探针 只在 {@code DDT_CHRONO_PARITY=1} 或 {@code -Dddt.chronoParity=true} 时跑
 *
 * <p>GameTest 的 test level 不加载晨昏线维度 所以指纹只能放在真正的专用服里量
 *
 * <p>只取 {@code WORLD_SURFACE_WG} 高度与地表群系 ID，不碰树 矿石 结构；这三样 c2me 自己也说
 * 同一个种子跑两次可能不一样。同一固定种子下 装与不装 c2me 各跑一次 两份指纹必须逐字节相同
 *
 * <p>跑完把指纹写到服务端目录的 {@code ddt-chrono-fingerprint.txt} 然后停服
 */
public final class ChronoParityProbe {
    private static final int SAMPLE_CHUNKS = 2;
    private static final double SAMPLE_DISTANCE = 2000.0D;
    private static final int[] FLUID_SAMPLE_YS = {-48, 0, 48};
    private static boolean done;

    private ChronoParityProbe() {}

    public static void runIfRequested(MinecraftServer server) {
        if (!"1".equals(System.getenv("DDT_CHRONO_PARITY")) && !Boolean.getBoolean("ddt.chronoParity")) {
            return;
        }
        if (done) {
            return;
        }
        done = true;
        server.execute(() -> run(server));
    }

    private static void run(MinecraftServer server) {
        ServerLevel level = server.getLevel(ModDimensions.CHRONO);
        if (level == null) {
            DawnDuskTerminal.LOGGER.error("DDT_CHRONO_PARITY chrono dimension is not loaded");
            server.halt(false);
            return;
        }

        try {
            ChronoLine line = ChronoLineState.serverLine();
            int originX = (int) Math.round(line.originX());
            int originZ = (int) Math.round(line.originZ());
            int normalX = (int) Math.round(line.normalX() * SAMPLE_DISTANCE);
            int normalZ = (int) Math.round(line.normalZ() * SAMPLE_DISTANCE);
            int[][] centers = {
                {originX, originZ},
                {originX + normalX, originZ + normalZ},
                {originX - normalX, originZ - normalZ}
            };

            StringBuilder full = new StringBuilder(8192);
            MessageDigest heights = digest();
            MessageDigest biomes = digest();
            MessageDigest fluids = digest();
            int columns = 0;

            for (int[] center : centers) {
                int baseChunkX = center[0] >> 4;
                int baseChunkZ = center[1] >> 4;
                for (int dx = 0; dx < SAMPLE_CHUNKS; dx++) {
                    for (int dz = 0; dz < SAMPLE_CHUNKS; dz++) {
                        int chunkX = baseChunkX + dx;
                        int chunkZ = baseChunkZ + dz;
                        // 只推到 SURFACE：不含雕刻 结构 特征 这三样才是 c2me 也承认跑两次会不一样的
                        ChunkAccess chunk = level.getChunkSource()
                            .getChunk(chunkX, chunkZ, ChunkStatus.SURFACE, true);
                        if (chunk == null) {
                            throw new IllegalStateException("chunk " + chunkX + "," + chunkZ + " is not available");
                        }
                        DawnDuskTerminal.LOGGER.info("DDT_CHRONO_PARITY chunk=({},{}) status={}",
                            chunkX, chunkZ, chunk.getPersistedStatus());
                        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
                        for (int x = 0; x < 16; x++) {
                            for (int z = 0; z < 16; z++) {
                                int worldX = chunk.getPos().getMinBlockX() + x;
                                int worldZ = chunk.getPos().getMinBlockZ() + z;
                                // 不信任高度图：c2me 可能改高度图跟踪，直接在方块层面找最高非空气
                                int height = level.getMinBuildHeight();
                                for (int y = level.getMaxBuildHeight() - 1; y >= level.getMinBuildHeight(); y--) {
                                    cursor.set(worldX, y, worldZ);
                                    if (!chunk.getBlockState(cursor).isAir()) {
                                        height = y + 1;
                                        break;
                                    }
                                }
                                int surfaceY = Math.max(height - 1, level.getMinBuildHeight());
                                Holder<Biome> biome = chunk.getNoiseBiome(
                                    QuartPos.fromBlock(worldX), QuartPos.fromBlock(surfaceY), QuartPos.fromBlock(worldZ));
                                String biomeId = biome.unwrapKey()
                                    .map(key -> key.location().toString()).orElse("?");

                                String heightLine = chunkX + ":" + chunkZ + ":" + x + ":" + z + ":" + height + "\n";
                                String biomeLine = chunkX + ":" + chunkZ + ":" + x + ":" + z + ":" + biomeId + "\n";
                                full.append(heightLine, 0, heightLine.length() - 1).append(' ').append(biomeId).append('\n');
                                heights.update(heightLine.getBytes(StandardCharsets.UTF_8));
                                biomes.update(biomeLine.getBytes(StandardCharsets.UTF_8));

                                // SURFACE 已经算完含水层，抽三层流体，重点看 c2me 的含水层优化有没有把
                                // default_fluid（portal_fluid）或岩浆调色板改掉
                                for (int fluidY : FLUID_SAMPLE_YS) {
                                    int y = Math.max(level.getMinBuildHeight(),
                                        Math.min(level.getMaxBuildHeight() - 1, fluidY));
                                    FluidState fluid = chunk.getFluidState(new BlockPos(worldX, y, worldZ));
                                    String fluidId = BuiltInRegistries.FLUID.getKey(fluid.getType()).toString();
                                    String fluidLine = chunkX + ":" + chunkZ + ":" + x + ":" + z + ":" + y + ":"
                                        + fluidId + "\n";
                                    full.append("fluid:").append(fluidLine);
                                    fluids.update(fluidLine.getBytes(StandardCharsets.UTF_8));
                                }
                                columns++;
                            }
                        }
                    }
                }
            }

            Path out = server.getServerDirectory().resolve("ddt-chrono-fingerprint.txt");
            Files.writeString(out, full.toString(), StandardCharsets.UTF_8);
            DawnDuskTerminal.LOGGER.info(
                "DDT_CHRONO_PARITY seed={} line={} normal=({},{}) columns={} height={} biome={} fluid={} combined={} file={}",
                level.getSeed(), line, normalX, normalZ, columns,
                hex(heights.digest()), hex(biomes.digest()), hex(fluids.digest()),
                hex(sha256(full.toString())), out);
        } catch (Throwable t) {
            DawnDuskTerminal.LOGGER.error("DDT_CHRONO_PARITY failed", t);
        } finally {
            server.halt(false);
        }
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] sha256(String value) {
        return digest().digest(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(Character.forDigit((b >> 4) & 0xF, 16));
            builder.append(Character.forDigit(b & 0xF, 16));
        }
        return builder.toString();
    }
}
