package com.dawnduskterminal.mixin;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.registry.ModDimensions;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.level.levelgen.structure.structures.MineshaftPieces;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 晨昏线维度放行了原版废弃矿井（见 {@code StructureExclusions}），但矿井刷出的箱车写死了
 * {@code minecraft:chests/abandoned_mineshaft}
 *
 * 在晨昏线维度里把那张表换成专用表，内容对着暮色森林空心矿山的箱子
 * （{@code twilightforest:hill_1/2/3}）
 */
@Mixin(MineshaftPieces.MineShaftCorridor.class)
public abstract class MineshaftChestLootMixin {
    private static final ResourceKey<LootTable> CHRONO_MINESHAFT =
            ResourceKey.create(Registries.LOOT_TABLE, DawnDuskTerminal.id("chests/chrono_mineshaft"));

    @Redirect(
            method =
                    "createChest(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/levelgen/structure/BoundingBox;Lnet/minecraft/util/RandomSource;IIILnet/minecraft/resources/ResourceKey;)Z",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/entity/vehicle/MinecartChest;setLootTable(Lnet/minecraft/resources/ResourceKey;J)V"))
    private void ddt$redirectMineshaftLoot(MinecartChest minecart, ResourceKey<LootTable> key, long seed) {
        if (ModDimensions.CHRONO.equals(minecart.level().dimension())) {
            key = CHRONO_MINESHAFT;
        }
        minecart.setLootTable(key, seed);
    }
}
