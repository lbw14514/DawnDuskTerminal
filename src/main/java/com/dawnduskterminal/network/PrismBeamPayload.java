package com.dawnduskterminal.network;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.client.PrismBeamParticles;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 棱镜权杖那一发的粒子**参数**：起点、主射线的终点与终点尘团的方块状态，以及每条分射线的同样两项
 *
 * <p>只发十来个点，线段上的光点与终点那团尘都由客户端自己生成（见 {@link PrismBeamParticles}）。
 * 走 {@code sendParticles} 的话一条主射线加五条分射线最多要几百颗粒子、就是几百个包，而且
 * {@code sendParticles} 只发给调用点 32 格内的玩家——射线打满 64 格时，连开枪的人自己都看不到
 * 终点那团尘。客户端自己铺就没有这两个问题
 *
 * <p>方块状态只发注册表 id（与 {@code BlockParticleOption} 的 stream codec 同一个做法），
 * 两边按同步过的注册表还原；0 是空气，客户端据此不画
 */
public record PrismBeamPayload(Vec3 origin, Ray main, List<Ray> splits) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PrismBeamPayload> TYPE =
            new CustomPacketPayload.Type<>(DawnDuskTerminal.id("prism_beam"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PrismBeamPayload> STREAM_CODEC =
            StreamCodec.of(PrismBeamPayload::encode, PrismBeamPayload::decode);

    /** 一条射线的终点与终点尘团要用的方块状态 id */
    public record Ray(Vec3 end, int blockStateId) {}

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PrismBeamPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> PrismBeamParticles.spawn(payload));
    }

    private static void encode(RegistryFriendlyByteBuf buf, PrismBeamPayload payload) {
        writeVec(buf, payload.origin());
        writeRay(buf, payload.main());
        buf.writeByte(payload.splits().size());
        for (Ray ray : payload.splits()) {
            writeRay(buf, ray);
        }
    }

    private static PrismBeamPayload decode(RegistryFriendlyByteBuf buf) {
        Vec3 origin = readVec(buf);
        Ray main = readRay(buf);
        int count = buf.readUnsignedByte();
        List<Ray> splits = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            splits.add(readRay(buf));
        }
        return new PrismBeamPayload(origin, main, List.copyOf(splits));
    }

    private static void writeRay(RegistryFriendlyByteBuf buf, Ray ray) {
        writeVec(buf, ray.end());
        buf.writeVarInt(ray.blockStateId());
    }

    private static Ray readRay(RegistryFriendlyByteBuf buf) {
        return new Ray(readVec(buf), buf.readVarInt());
    }

    private static void writeVec(RegistryFriendlyByteBuf buf, Vec3 vec) {
        buf.writeFloat((float) vec.x);
        buf.writeFloat((float) vec.y);
        buf.writeFloat((float) vec.z);
    }

    private static Vec3 readVec(RegistryFriendlyByteBuf buf) {
        return new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat());
    }
}
