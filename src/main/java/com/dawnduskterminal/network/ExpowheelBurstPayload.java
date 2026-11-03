package com.dawnduskterminal.network;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.client.ExpowheelParticles;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 博览轮舞那片飞轮寿命到点时的炸开参数：位置加弹体当时的速度
 *
 * <p>粒子由客户端在本地铺（见 {@link ExpowheelParticles}）。为什么不在客户端自己触发：
 * 弹体一 tick 飞两格，出客户端跟踪范围（4 区块）只要一秒多，等到 120 tick 寿命到点时客户端早就没有这个实体了，
 * 所以「到点」这件事只能由服务端说，客户端照着撒。
 *
 * <p>速度也一起发：环要顺着飞行方向甩出去，而不是原地炸开。
 */
public record ExpowheelBurstPayload(double x, double y, double z, float velocityX, float velocityY, float velocityZ)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ExpowheelBurstPayload> TYPE =
            new CustomPacketPayload.Type<>(DawnDuskTerminal.id("expowheel_burst"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ExpowheelBurstPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.DOUBLE,
                    ExpowheelBurstPayload::x,
                    ByteBufCodecs.DOUBLE,
                    ExpowheelBurstPayload::y,
                    ByteBufCodecs.DOUBLE,
                    ExpowheelBurstPayload::z,
                    ByteBufCodecs.FLOAT,
                    ExpowheelBurstPayload::velocityX,
                    ByteBufCodecs.FLOAT,
                    ExpowheelBurstPayload::velocityY,
                    ByteBufCodecs.FLOAT,
                    ExpowheelBurstPayload::velocityZ,
                    ExpowheelBurstPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ExpowheelBurstPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ExpowheelParticles.spawnBurst(
                payload.x(), payload.y(), payload.z(), payload.velocityX(), payload.velocityY(), payload.velocityZ()));
    }
}
