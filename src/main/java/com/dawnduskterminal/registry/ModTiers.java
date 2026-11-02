package com.dawnduskterminal.registry;

import java.util.function.Supplier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

/**
 * 工具分级
 *
 * <p>1.21 的 {@link Tier} 不再有「挖掘等级」这个数字，改成一张「挖不动的方块」标签，所以数值只需要
 * 耐久 速度 攻击加成 附魔值 修理材料五项
 *
 * <p>天门松原木对标铁，苍穹锭与砷铅铁锭都对标钻石
 *
 * <p>T2 与 T3 都用 {@code INCORRECT_FOR_NETHERITE_TOOL}：原版没有比下界合金更高的挖掘等级标签，
 * 两档的差别放在耐久 速度 攻击加成 附魔值上
 */
public final class ModTiers {
    /** 铁级 */
    public static final Tier SKYGATE_PINE = new SimpleTier(
            BlockTags.INCORRECT_FOR_IRON_TOOL,
            250,
            6.0F,
            2.0F,
            14,
            () -> Ingredient.of(ModTerrainBlocks.SKYGATE_PINE_LOG.get()));

    /** 钻石级 */
    public static final Tier CANOPY = new SimpleTier(
            BlockTags.INCORRECT_FOR_DIAMOND_TOOL,
            1561,
            8.0F,
            3.0F,
            10,
            () -> Ingredient.of(ModItems.CANOPY_INGOT.get()));

    /** 钻石级 */
    public static final Tier CARMINITE = new SimpleTier(
            BlockTags.INCORRECT_FOR_DIAMOND_TOOL,
            1561,
            8.0F,
            3.0F,
            10,
            () -> Ingredient.of(ModItems.CARMINITE_INGOT.get()));

    /** T2 绯色钼，高于钻石 */
    public static final Tier SCARLET_MOLYBDENUM = new SimpleTier(
            BlockTags.INCORRECT_FOR_NETHERITE_TOOL,
            2031,
            9.0F,
            3.5F,
            15,
            () -> Ingredient.of(ModItems.SCARLET_MOLYBDENUM_INGOT.get()));

    /** T2 狄拉克，逃课材料，数值与绯色钼同档 */
    public static final Tier DIRAC = new SimpleTier(
            BlockTags.INCORRECT_FOR_NETHERITE_TOOL,
            2031,
            9.0F,
            3.5F,
            15,
            () -> Ingredient.of(ModItems.DIRAC_INGOT.get()));

    /** T3 䤧，毕业级 */
    public static final Tier HUI = new SimpleTier(
            BlockTags.INCORRECT_FOR_NETHERITE_TOOL,
            2600,
            10.0F,
            4.0F,
            18,
            () -> Ingredient.of(ModItems.HUI_INGOT.get()));

    private ModTiers() {}

    private record SimpleTier(
            TagKey<Block> incorrectBlocksForDrops,
            int uses,
            float speed,
            float attackDamageBonus,
            int enchantmentValue,
            Supplier<Ingredient> repairIngredient)
            implements Tier {
        @Override
        public int getUses() {
            return this.uses;
        }

        @Override
        public float getSpeed() {
            return this.speed;
        }

        @Override
        public float getAttackDamageBonus() {
            return this.attackDamageBonus;
        }

        @Override
        public TagKey<Block> getIncorrectBlocksForDrops() {
            return this.incorrectBlocksForDrops;
        }

        @Override
        public int getEnchantmentValue() {
            return this.enchantmentValue;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return this.repairIngredient.get();
        }
    }
}
