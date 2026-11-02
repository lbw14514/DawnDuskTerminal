package com.dawnduskterminal.network;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.client.Experiment055Sounds;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 试验物品 055 的「这一刻该响哪一支」：施放者实体 id 加档位
 *
 * <p>档位只有四种，见下面的四个常量：没充满循环 {@code charge}，充满那一下放一次 {@code full}，
 * 之后循环 {@code geiger}，松手（或死亡、换物品、物品碎掉、换维度）发 {@code STOP} 收声。
 * 服务端每秒重发一次当前档位，客户端按施放者认人、同一档位重复收到时什么都不做，所以重发不会叠音；
 * 从 {@code charge} 换成 {@code geiger}、或是发 {@code FULL}，都是在同一个实体上换曲
 *
 * <p>循环音只能由客户端自己的 {@code SoundInstance} 维持（原版没有「服务端让客户端循环播放」的路子），
 * 所以这条链路与 095 的镇魂曲是同一个套路，见 {@link Experiment055Sounds}
 */
public record Experiment055SoundPayload(int casterId, int mode) implements CustomPacketPayload {
    /** 收声 */
    public static final int MODE_STOP = -1;
    /** 充能中：循环 {@code experiment_055_charge} */
    public static final int MODE_CHARGE = 0;
    /** 充满那一下：放一次 {@code experiment_055_full} */
    public static final int MODE_FULL = 1;
    /** 充满之后：循环 {@code experiment_055_geiger} */
    public static final int MODE_GEIGER = 2;

    public static final CustomPacketPayload.Type<Experiment055SoundPayload> TYPE =
            new CustomPacketPayload.Type<>(DawnDuskTerminal.id("experiment_055_sound"));

    public static final StreamCodec<RegistryFriendlyByteBuf, Experiment055SoundPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    Experiment055SoundPayload::casterId,
                    ByteBufCodecs.VAR_INT,
                    Experiment055SoundPayload::mode,
                    Experiment055SoundPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(Experiment055SoundPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Experiment055Sounds.handle(payload.casterId(), payload.mode()));
    }
}
