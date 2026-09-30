package com.dawnduskterminal.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    public static final String VERSION = "1";

    private ModNetwork() {}

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToClient(ChronoLinePayload.TYPE, ChronoLinePayload.STREAM_CODEC, ChronoLinePayload::handle);
    }
}
