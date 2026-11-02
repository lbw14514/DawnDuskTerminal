package com.dawnduskterminal.network;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.client.Experiment055Particles;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 试验物品 055 每 tick 那一条 enchant 射线的**参数**：施放者实体 id 加方向与长度
 *
 * <p>粒子本身由客户端本地生成（见 {@link Experiment055Particles}）——一颗一包地
 * {@code sendParticles} 每 tick 要发几十上百个包，而这里每个施放者每 tick 只发一个包，
 * 发往 32 格内（与 {@code sendParticles} 的可见距离同档）
 *
 * <p>方向在服务端摇，所以同一圈人看到的是同一条射线；长度是 {@code 1.5 × 当时的影响半径}，
 * 由服务端按充能进度算好一并发下来，客户端不猜
 */
public record Experiment055RayPayload(int casterId, float directionX, float directionY, float directionZ,
                                      float length) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<Experiment055RayPayload> TYPE =
        new CustomPacketPayload.Type<>(DawnDuskTerminal.id("experiment_055_ray"));

    public static final StreamCodec<RegistryFriendlyByteBuf, Experiment055RayPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Experiment055RayPayload::casterId,
            ByteBufCodecs.FLOAT, Experiment055RayPayload::directionX,
            ByteBufCodecs.FLOAT, Experiment055RayPayload::directionY,
            ByteBufCodecs.FLOAT, Experiment055RayPayload::directionZ,
            ByteBufCodecs.FLOAT, Experiment055RayPayload::length,
            Experiment055RayPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(Experiment055RayPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Experiment055Particles.spawn(
            payload.casterId(),
            payload.directionX(), payload.directionY(), payload.directionZ(),
            payload.length()));
    }
}
