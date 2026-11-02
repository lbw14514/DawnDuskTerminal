package com.dawnduskterminal.crafting;

import com.mojang.serialization.MapCodec;
import java.util.stream.Stream;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;

/**
 * 匹配任意一种效果里带 {@link MobEffects#POISON} 的药水，饮用 喷溅 滞留都算
 *
 * 这样砷铅铁混合物的配方能接受任何毒药水，而不是只认基础的 {@code minecraft:poison}
 */
public final class PoisonPotionIngredient implements ICustomIngredient {
    public static final PoisonPotionIngredient INSTANCE = new PoisonPotionIngredient();
    public static final MapCodec<PoisonPotionIngredient> CODEC = MapCodec.unit(INSTANCE);
    public static final IngredientType<PoisonPotionIngredient> TYPE = new IngredientType<>(CODEC);

    private PoisonPotionIngredient() {}

    @Override
    public boolean test(ItemStack stack) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null) {
            return false;
        }
        for (var effect : contents.getAllEffects()) {
            if (effect.getEffect().value() == MobEffects.POISON.value()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Stream<ItemStack> getItems() {
        return Stream.of(
                PotionContents.createItemStack(Items.POTION, Potions.POISON),
                PotionContents.createItemStack(Items.SPLASH_POTION, Potions.POISON),
                PotionContents.createItemStack(Items.LINGERING_POTION, Potions.POISON));
    }

    @Override
    public boolean isSimple() {
        return false;
    }

    @Override
    public IngredientType<?> getType() {
        return TYPE;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof PoisonPotionIngredient;
    }

    @Override
    public int hashCode() {
        return PoisonPotionIngredient.class.hashCode();
    }
}
