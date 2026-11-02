package com.dawnduskterminal.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    public static final String VERSION = "1";

    private ModNetwork() {}

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToClient(ChronoLinePayload.TYPE, ChronoLinePayload.STREAM_CODEC, ChronoLinePayload::handle);
        // 试验物品 095 那支循环曲的起停：服务端只发包，循环音由客户端自己的 SoundInstance 维持
        registrar.playToClient(RequiemStartPayload.TYPE, RequiemStartPayload.STREAM_CODEC, RequiemStartPayload::handle);
        registrar.playToClient(RequiemStopPayload.TYPE, RequiemStopPayload.STREAM_CODEC, RequiemStopPayload::handle);
        // 试验物品 055 的「这一刻该响哪一支」：charge / full / geiger / 收声四档，同样由客户端维持循环
        registrar.playToClient(
                Experiment055SoundPayload.TYPE,
                Experiment055SoundPayload.STREAM_CODEC,
                Experiment055SoundPayload::handle);
        // 055 每 tick 那一条 enchant 射线：只发方向与长度，粒子由客户端自己铺
        registrar.playToClient(
                Experiment055RayPayload.TYPE, Experiment055RayPayload.STREAM_CODEC, Experiment055RayPayload::handle);
    }
}
