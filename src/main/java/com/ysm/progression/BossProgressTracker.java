package com.ysm.progression;

import com.ysm.registry.ModAttachments;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class BossProgressTracker {
    private BossProgressTracker() {}

    public static boolean unlocked(Player player) {
        return player.getData(ModAttachments.BOSS_PROGRESS).anyDefeated();
    }

    public static void onBossKilled(LivingEntity dead, Player killer) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(dead.getType());
        BossKind kind = BossKind.of(id);
        if (kind == null) {
            return;
        }
        BossProgress current = killer.getData(ModAttachments.BOSS_PROGRESS);
        if (current.anyDefeated()) {
            return;
        }
        killer.setData(ModAttachments.BOSS_PROGRESS, current.with(kind));
        if (killer instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(Component.translatable("message.ysm.gate_unlocked"), false);
        }
    }
}
