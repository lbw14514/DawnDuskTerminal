package com.ysm.network;

import com.ysm.Ysm;
import com.ysm.world.ChronoLine;
import com.ysm.world.ChronoLineState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ChronoLinePayload(ChronoLine line) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ChronoLinePayload> TYPE =
        new CustomPacketPayload.Type<>(Ysm.id("chrono_line"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ChronoLinePayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.DOUBLE, payload -> payload.line.originX(),
        ByteBufCodecs.DOUBLE, payload -> payload.line.originZ(),
        ByteBufCodecs.DOUBLE, payload -> payload.line.normalX(),
        ByteBufCodecs.DOUBLE, payload -> payload.line.normalZ(),
        (originX, originZ, normalX, normalZ) -> new ChronoLinePayload(new ChronoLine(originX, originZ, normalX, normalZ))
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ChronoLinePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ChronoLineState.setClientLine(payload.line()));
    }
}
