package com.dawnduskterminal.network;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.client.RequiemSounds;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 「把某个施放者身上那支曲子收掉」
 *
 * <p>松手、死亡、下线、换维度、物品碎掉，都是由服务端的 {@code RequiemProcess} 发现之后发这一个包；
 * 客户端收到就去 {@link RequiemSounds#stop} 里把那一支停掉。按实体 id 认人，所以附近几个人同时
 * 按住 095 时各停各的，不会一停全停
 *
 * <p>包发往施放者周围 64 格（比听得见的 32 格宽一圈）：按住期间走远的人已经听不见，
 * 但声道还占着，得给他一颗停止包；再远的那一小撮由客户端自己按距离收声，见
 * {@code RequiemSoundInstance#CUTOFF}
 */
public record RequiemStopPayload(int casterId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RequiemStopPayload> TYPE =
            new CustomPacketPayload.Type<>(DawnDuskTerminal.id("requiem_stop"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequiemStopPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, RequiemStopPayload::casterId, RequiemStopPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequiemStopPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> RequiemSounds.stop(payload.casterId()));
    }
}
