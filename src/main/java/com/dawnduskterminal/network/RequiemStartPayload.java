package com.dawnduskterminal.network;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.client.RequiemSounds;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 「让某个施放者身上那支曲子开响」：施放者的实体 id 加曲子下标
 *
 * <p>服务端只在开始按住那一刻与之后每秒各发一次，发给施放者周围 32 格内的所有玩家（含他自己）；
 * 客户端收到就去 {@link RequiemSounds#start} 里起一支循环音。重复的包是幂等的——
 * 客户端看到同一个施放者已经在响就直接忽略，所以「每秒重发」不会叠音
 *
 * <p>曲子下标对应 {@code ModSounds.REQUIEM_TRACKS} 那张有序表，由服务端摇出来，客户端照单点菜；
 * 下标越界时客户端干脆不出声
 */
public record RequiemStartPayload(int casterId, int track) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RequiemStartPayload> TYPE =
        new CustomPacketPayload.Type<>(DawnDuskTerminal.id("requiem_start"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequiemStartPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RequiemStartPayload::casterId,
            ByteBufCodecs.VAR_INT, RequiemStartPayload::track,
            RequiemStartPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequiemStartPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> RequiemSounds.start(payload.casterId(), payload.track()));
    }
}
