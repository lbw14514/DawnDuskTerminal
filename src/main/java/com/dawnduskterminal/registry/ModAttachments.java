package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.portal.PortalState;
import com.dawnduskterminal.progression.BossProgress;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, DawnDuskTerminal.MOD_ID);

    public static final Supplier<AttachmentType<BossProgress>> BOSS_PROGRESS = ATTACHMENTS.register(
        "boss_progress",
        () -> AttachmentType.builder(BossProgress::empty)
            .serialize(BossProgress.CODEC)
            .copyOnDeath()
            .build());

    public static final Supplier<AttachmentType<PortalState>> PORTAL_STATE = ATTACHMENTS.register(
        "portal_state",
        () -> AttachmentType.builder(PortalState::empty)
            .serialize(PortalState.CODEC)
            .build());

    private ModAttachments() {}

    public static void register(IEventBus bus) {
        ATTACHMENTS.register(bus);
    }
}
