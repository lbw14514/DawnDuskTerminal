package com.dawnduskterminal.mixin;

import com.dawnduskterminal.registry.ModBlocks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让合成台界面认方块标签而不是写死的原版工作台
 *
 * <p>原版 {@code CraftingMenu#stillValid} 是
 * {@code stillValid(this.access, player, Blocks.CRAFTING_TABLE)}，服务端每个刻都问一次，
 * 答 false 就直接关界面。所以任何不是 {@code minecraft:crafting_table} 的合成台都开不住：
 * 界面刚打开就被踢出来
 *
 * <p>整条替换成读 {@link ModBlocks#CRAFTING_TABLES}。那个标签里原版工作台和
 * {@code dawnduskterminal:experiment_001} 都在，所以原版工作台行为不变。
 * 距离判定照抄原版：{@code Player#canInteractWithBlock(pos, 4.0)}
 *
 * <p>只影响 {@link CraftingMenu}，玩家自带的 2×2 界面是另一个菜单类，不经过这里
 */
@Mixin(CraftingMenu.class)
public abstract class CraftingMenuMixin {
    @Shadow
    @Final
    private ContainerLevelAccess access;

    @Inject(method = "stillValid(Lnet/minecraft/world/entity/player/Player;)Z", at = @At("HEAD"), cancellable = true)
    private void ddt$stillValidForCraftingTables(Player player, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(this.access.evaluate(
                (level, pos) ->
                        level.getBlockState(pos).is(ModBlocks.CRAFTING_TABLES) && player.canInteractWithBlock(pos, 4.0),
                true));
    }
}
