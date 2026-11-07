package com.dawnduskterminal.mixin;

import com.dawnduskterminal.entity.GhastPaintingEntity;
import com.dawnduskterminal.registry.ModPaintingVariants;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.decoration.PaintingVariant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 原版画实体不许挂我们的变体
 *
 * <p>摆放这条路已经堵了：原版画物品只从 {@code #minecraft:painting_variant/placeable}
 * 里挑变体，我们的变体不在那个标签里。剩下的是手改那条——{@code /summon minecraft:painting
 * {variant:"dawnduskterminal:painting_ghast_1"}}，或者旧存档里已经被改过的原版画。
 * 这里把这种赋值整条吞掉：画留在它原来的变体上，不会变成我们的画
 *
 * <p>本模组的画一律拦，判定交给 {@link ModPaintingVariants#isModVariant}
 *
 * <p>自己的实体不受影响：{@link GhastPaintingEntity} 覆盖了 {@code setVariant}，
 * 它调父类时这里放行
 */
@Mixin(Painting.class)
public abstract class PaintingVariantGuardMixin {
    @Inject(method = "setVariant(Lnet/minecraft/core/Holder;)V", at = @At("HEAD"), cancellable = true)
    private void ddt$rejectForeignVariant(Holder<PaintingVariant> variant, CallbackInfo ci) {
        if ((Object) this instanceof GhastPaintingEntity) {
            return;
        }
        if (variant.unwrapKey().filter(ModPaintingVariants::isModVariant).isPresent()) {
            ci.cancel();
        }
    }
}
