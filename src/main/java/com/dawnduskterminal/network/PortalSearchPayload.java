package com.dawnduskterminal.network;

import com.dawnduskterminal.DawnDuskTerminal;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PortalSearchPayload(boolean searching) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PortalSearchPayload> TYPE =
        new CustomPacketPayload.Type<>(DawnDuskTerminal.id("portal_search"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PortalSearchPayload> STREAM_CODEC =
        StreamCodec.composite(ByteBufCodecs.BOOL, PortalSearchPayload::searching, PortalSearchPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PortalSearchPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.dawnduskterminal.client.PortalDistortion.setSearching(payload.searching()));
    }
}
