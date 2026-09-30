package com.ysm.world;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;

public final class ChronoLineState extends SavedData {
    private static final String DATA_NAME = "ysm_chrono_line";
    private static ChronoLine serverLine;
    private static ChronoLine clientLine;

    private ChronoLine line;

    public ChronoLineState() {
        this.line = ChronoLine.create(RandomSource.create());
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
            .orElseGet(ChronoLineState::new);
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
            new SavedData.Factory<>(ChronoLineState::new, ChronoLineState::load, null), DATA_NAME);
    }

    public static ChronoLine serverLine() {
        return serverLine != null ? serverLine : ChronoLine.fallback();
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
}
