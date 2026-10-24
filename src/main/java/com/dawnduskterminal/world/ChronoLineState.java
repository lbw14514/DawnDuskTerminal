package com.dawnduskterminal.world;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;

public final class ChronoLineState extends SavedData {
    private static final String DATA_NAME = "dawnduskterminal_chrono_line";
    // 这两份快照会被 worldgen 工作线程与渲染线程读取 主线程写入 必须 volatile
    private static volatile ChronoLine serverLine;
    private static volatile ChronoLine clientLine;

    private ChronoLine line;

    public ChronoLineState(long seed) {
        this.line = ChronoLine.create(RandomSource.create(seed));
        setDirty();
    }

    public ChronoLineState(ChronoLine line) {
        this.line = line;
    }

    public ChronoLine line() {
        return this.line;
    }

    public void setLine(ChronoLine line) {
        if (!line.equals(this.line)) {
            this.line = line;
            setDirty();
        }
    }

    public static ChronoLineState load(CompoundTag tag, HolderLookup.Provider registries) {
        return ChronoLine.CODEC.parse(NbtOps.INSTANCE, tag.get("line"))
            .result()
            .map(ChronoLineState::new)
            .orElseGet(() -> new ChronoLineState(ChronoLine.fallback()));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ChronoLine.CODEC.encodeStart(NbtOps.INSTANCE, this.line)
            .result()
            .ifPresent(t -> tag.put("line", t));
        return tag;
    }

    public static ChronoLineState get(ServerLevel anyLevel) {
        ServerLevel overworld = anyLevel.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(() -> new ChronoLineState(overworld.getSeed()), ChronoLineState::load, null), DATA_NAME);
    }

    public static ChronoLine serverLine() {
        return serverLine != null ? serverLine : ChronoLine.fallback();
    }

    public static boolean hasServerLine() {
        return serverLine != null;
    }

    public static void setServerLine(ChronoLine line) {
        serverLine = line;
    }

    public static ChronoLine clientLine() {
        return clientLine != null ? clientLine : serverLine();
    }

    public static void setClientLine(ChronoLine line) {
        clientLine = line;
    }

    /** 客户端断开连接时清空 否则进新存档头几帧会拿上一档的线算天光与天色 */
    public static void resetClientLine() {
        clientLine = null;
    }
}
