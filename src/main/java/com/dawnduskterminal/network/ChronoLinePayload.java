package com.dawnduskterminal.network;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.world.ChronoLine;
import com.dawnduskterminal.world.ChronoLineState;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ChronoLinePayload(ChronoLine line) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ChronoLinePayload> TYPE =
        new CustomPacketPayload.Type<>(DawnDuskTerminal.id("chrono_line"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ChronoLinePayload> STREAM_CODEC = StreamCodec.of(
        (buffer, payload) -> {
            buffer.writeDouble(payload.line.originX());
            buffer.writeDouble(payload.line.originZ());
            buffer.writeDouble(payload.line.normalX());
            buffer.writeDouble(payload.line.normalZ());
        },
        buffer -> {
            double originX = buffer.readDouble();
            double originZ = buffer.readDouble();
            double normalX = buffer.readDouble();
            double normalZ = buffer.readDouble();
            // NaN 与无穷会让天光 群系与假太阳一路传播成不可用的渲染状态 在解码处直接拒绝
            if (!Double.isFinite(originX) || !Double.isFinite(originZ)
                || !Double.isFinite(normalX) || !Double.isFinite(normalZ)) {
                throw new DecoderException("Rejected a non-finite chrono line from the server");
            }
            return new ChronoLinePayload(new ChronoLine(originX, originZ, normalX, normalZ));
        });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ChronoLinePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ChronoLineState.setClientLine(payload.line()));
    }
}
