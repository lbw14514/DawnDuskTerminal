package com.dawnduskterminal.gametest;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.entity.GhastPaintingEntity;
import com.dawnduskterminal.registry.ModEntities;
import com.dawnduskterminal.registry.ModItems;
import com.dawnduskterminal.registry.ModPaintingVariants;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.PaintingVariantTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 恶魂画 #1 的规格与互不通用
 *
 * <p>画的「能不能被原版物品放下」只取决于一个事实：原版 {@code Painting#create} 从
 * {@code #minecraft:painting_variant/placeable} 这个标签里挑变体。所以「原版放不出我们的画」
 * 直接断言成「我们的变体不在那个标签里」，比摆一面墙去撞随机数可靠
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class PaintingTests {
    private static final String VARIANT = "dawnduskterminal:painting_ghast_1";

    @GameTest(template = "ddt_empty")
    public static void variantIsThreeByTwo(GameTestHelper helper) {
        PaintingVariant variant =
                ModPaintingVariants.holder(helper.getLevel().registryAccess()).value();
        helper.assertTrue(variant.width() == 3, VARIANT + " must be 3 blocks wide, got " + variant.width());
        helper.assertTrue(variant.height() == 2, VARIANT + " must be 2 blocks tall, got " + variant.height());
        helper.assertTrue(
                variant.assetId().equals(DawnDuskTerminal.id("painting_ghast_1")),
                VARIANT + " must render dawnduskterminal:painting_ghast_1, got " + variant.assetId());
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void vanillaPaintingItemCannotPlaceIt(GameTestHelper helper) {
        boolean placeable =
                ModPaintingVariants.holder(helper.getLevel().registryAccess()).is(PaintingVariantTags.PLACEABLE);
        helper.assertFalse(
                placeable,
                VARIANT + " must not be in #minecraft:painting_variant/placeable, "
                        + "otherwise the vanilla painting item could place it");
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void entityIsLockedToItsVariant(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GhastPaintingEntity painting = new GhastPaintingEntity(
                ModEntities.GHAST_PAINTING_1.get(), level, helper.absolutePos(new BlockPos(2, 2, 2)), Direction.SOUTH);
        helper.assertTrue(
                painting.getVariant().is(ModPaintingVariants.GHAST_1),
                "a fresh ghast painting must already hold its own variant");

        Holder<PaintingVariant> vanilla = level.registryAccess()
                .registryOrThrow(Registries.PAINTING_VARIANT)
                .getHolderOrThrow(ResourceKey.create(
                        Registries.PAINTING_VARIANT, ResourceLocation.withDefaultNamespace("kebab")));
        painting.setVariant(vanilla);
        helper.assertTrue(
                painting.getVariant().is(ModPaintingVariants.GHAST_1),
                "setVariant must be ignored, the ghast painting only ever shows its own variant");

        helper.assertTrue(
                painting.getPickResult().is(ModItems.PAINTING_GHAST_1.get()),
                "pick block on the ghast painting must give its own item");
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void vanillaPaintingRefusesTheVariant(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Painting vanilla = new Painting(EntityType.PAINTING, level);
        vanilla.setVariant(ModPaintingVariants.holder(level.registryAccess()));
        helper.assertFalse(
                vanilla.getVariant().is(ModPaintingVariants.GHAST_1),
                "a vanilla painting must never accept " + VARIANT + " (PaintingVariantGuardMixin)");
        helper.succeed();
    }

    /**
     * 建一面 3x2 的墙，从南面贴着放一幅画：实体必须是自己这个类型，变体是 3x2，朝向南，物品减一
     */
    @GameTest(template = "ddt_empty")
    public static void itemPlacesThePainting(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos clickedRel = new BlockPos(2, 2, 3);
        for (int x = 1; x <= 3; x++) {
            for (int y = 2; y <= 3; y++) {
                helper.setBlock(new BlockPos(x, y, 3), Blocks.STONE);
            }
        }

        BlockPos clickedAbs = helper.absolutePos(clickedRel);
        ItemStack stack = new ItemStack(ModItems.PAINTING_GHAST_1.get());
        UseOnContext context = new UseOnContext(
                level,
                null,
                InteractionHand.MAIN_HAND,
                stack,
                new BlockHitResult(Vec3.atCenterOf(clickedAbs), Direction.SOUTH, clickedAbs, false));

        InteractionResult result = ModItems.PAINTING_GHAST_1.get().useOn(context);
        helper.assertTrue(result.consumesAction(), "using the item on a wall must consume the action");
        helper.assertTrue(stack.isEmpty(), "placing the painting must consume the item");

        BlockPos anchorAbs = clickedAbs.relative(Direction.SOUTH);
        List<GhastPaintingEntity> placed = level.getEntitiesOfClass(
                GhastPaintingEntity.class, AABB.ofSize(Vec3.atCenterOf(anchorAbs), 6.0D, 6.0D, 6.0D));
        helper.assertTrue(placed.size() == 1, "expected exactly one ghast painting, got " + placed.size());

        GhastPaintingEntity painting = placed.get(0);
        helper.assertTrue(
                painting.getDirection() == Direction.SOUTH,
                "painting must face the clicked side, got " + painting.getDirection());
        helper.assertTrue(
                painting.getVariant().is(ModPaintingVariants.GHAST_1), "placed painting must show " + VARIANT);
        helper.assertTrue(
                painting.getBoundingBox().getXsize() == 3.0D,
                "3x2 painting must be 3 blocks wide, got "
                        + painting.getBoundingBox().getXsize());
        helper.assertTrue(
                painting.getBoundingBox().getYsize() == 2.0D,
                "3x2 painting must be 2 blocks tall, got "
                        + painting.getBoundingBox().getYsize());
        helper.succeed();
    }
}
