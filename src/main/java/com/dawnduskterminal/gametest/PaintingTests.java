package com.dawnduskterminal.gametest;

import com.dawnduskterminal.entity.GhastPaintingEntity;
import com.dawnduskterminal.item.GhastPaintingItem;
import com.dawnduskterminal.registry.ModPaintingVariants;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.PaintingVariantTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 十幅画的规格与互不通用
 *
 * <p>画能不能被原版物品放下只取决于一个事实：原版 {@code Painting#create} 从
 * {@code #minecraft:painting_variant/placeable} 这个标签里挑变体。所以「原版放不出我们的画」
 * 直接断言成「我们的变体不在那个标签里」，比摆一面墙去撞随机数可靠
 *
 * <p>十幅画一律照 {@link #SPECS} 这张表走，新增一幅只需要加一行
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class PaintingTests {
    /** 十幅画与它们在数据包里写的宽高（{@code data/dawnduskterminal/painting_variant/*.json}） */
    private static final List<Spec> SPECS = List.of(
            new Spec(ModPaintingVariants.GHAST_1, 3, 2),
            new Spec(ModPaintingVariants.GHAST_2, 2, 3),
            new Spec(ModPaintingVariants.GHAST_2_SUB, 1, 2),
            new Spec(ModPaintingVariants.GHAST_3, 3, 2),
            new Spec(ModPaintingVariants.GHAST_4, 2, 3),
            new Spec(ModPaintingVariants.GHAST_5, 2, 3),
            new Spec(ModPaintingVariants.GHAST_6, 2, 3),
            new Spec(ModPaintingVariants.GHAST_7, 2, 3),
            new Spec(ModPaintingVariants.GHAST_8, 2, 3),
            new Spec(ModPaintingVariants.GHAST_9, 6, 4));

    /** 一幅画的期望值：种类与 JSON 里那一对宽高 */
    private record Spec(ModPaintingVariants.Kind kind, int width, int height) {
        String name() {
            return this.kind.variant().location().toString();
        }
    }

    /**
     * 摆放测试的锚点：墙面正中那一格（结构是 16 格见方，墙铺在 z=3）。
     * 放在正中是为了让六格宽四格高的 {@code painting_feuer} 四个方向都落在墙内
     */
    private static final BlockPos WALL_ANCHOR = new BlockPos(8, 7, 3);

    @GameTest(template = "ddt_empty")
    public static void variantsMatchTheirTextures(GameTestHelper helper) {
        for (Spec spec : SPECS) {
            PaintingVariant variant = ModPaintingVariants.holder(
                            helper.getLevel().registryAccess(), spec.kind())
                    .value();
            helper.assertTrue(
                    variant.width() == spec.width(),
                    spec.name() + " must be " + spec.width() + " blocks wide, got " + variant.width());
            helper.assertTrue(
                    variant.height() == spec.height(),
                    spec.name() + " must be " + spec.height() + " blocks tall, got " + variant.height());
            helper.assertTrue(
                    variant.assetId().equals(spec.kind().variant().location()),
                    spec.name() + " must render " + spec.kind().variant().location() + ", got " + variant.assetId());
        }
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void vanillaPaintingItemCannotPlaceThem(GameTestHelper helper) {
        for (Spec spec : SPECS) {
            boolean placeable = ModPaintingVariants.holder(helper.getLevel().registryAccess(), spec.kind())
                    .is(PaintingVariantTags.PLACEABLE);
            helper.assertFalse(
                    placeable,
                    spec.name() + " must not be in #minecraft:painting_variant/placeable, "
                            + "otherwise the vanilla painting item could place it");
        }
        helper.succeed();
    }

    /** 一件物品只对应一幅画，十件两两不同 */
    @GameTest(template = "ddt_empty")
    public static void itemsAreBoundToOnePainting(GameTestHelper helper) {
        Set<Item> seen = new HashSet<>();
        for (Spec spec : SPECS) {
            Item item = spec.kind().item().get();
            helper.assertTrue(
                    item instanceof GhastPaintingItem, spec.name() + " must be placed by a ghast painting item");
            helper.assertTrue(
                    ((GhastPaintingItem) item).kind() == spec.kind(),
                    spec.name() + "'s item must point back at the same painting");
            helper.assertTrue(seen.add(item), "two paintings must not share one item, " + spec.name() + " does");
        }
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void entitiesAreLockedToTheirVariants(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Holder<PaintingVariant> vanilla = level.registryAccess()
                .registryOrThrow(Registries.PAINTING_VARIANT)
                .getHolderOrThrow(ResourceKey.create(
                        Registries.PAINTING_VARIANT, ResourceLocation.withDefaultNamespace("kebab")));
        BlockPos anchor = helper.absolutePos(new BlockPos(8, 2, 8));
        for (Spec spec : SPECS) {
            GhastPaintingEntity painting =
                    new GhastPaintingEntity(entityType(helper, spec), level, spec.kind(), anchor, Direction.SOUTH);
            helper.assertTrue(painting.kind() == spec.kind(), spec.name() + "'s entity must carry its own kind");
            helper.assertTrue(
                    painting.getVariant().is(spec.kind().variant()),
                    "a fresh " + spec.name() + " entity must already hold its own variant");
            helper.assertTrue(
                    painting.getBoundingBox().getXsize() == (double) spec.width(),
                    spec.name() + " must be " + spec.width() + " blocks wide, got "
                            + painting.getBoundingBox().getXsize());
            helper.assertTrue(
                    painting.getBoundingBox().getYsize() == (double) spec.height(),
                    spec.name() + " must be " + spec.height() + " blocks tall, got "
                            + painting.getBoundingBox().getYsize());

            painting.setVariant(vanilla);
            helper.assertTrue(
                    painting.getVariant().is(spec.kind().variant()),
                    "setVariant must be ignored, " + spec.name() + " only ever shows its own variant");
            helper.assertTrue(
                    painting.getPickResult().is(spec.kind().item().get()),
                    "pick block on " + spec.name() + " must give its own item, got " + painting.getPickResult());
        }
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void vanillaPaintingRefusesTheVariants(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (Spec spec : SPECS) {
            Painting vanilla = new Painting(EntityType.PAINTING, level);
            vanilla.setVariant(ModPaintingVariants.holder(level.registryAccess(), spec.kind()));
            helper.assertFalse(
                    vanilla.getVariant().is(spec.kind().variant()),
                    "a vanilla painting must never accept " + spec.name() + " (PaintingVariantGuardMixin)");
        }
        helper.succeed();
    }

    /**
     * 建一整面墙，十幅画轮流贴着墙面放一次：实体必须是自己那个类型、自己的变体、自己的宽高，
     * 朝向南，物品减一
     *
     * <p>一次只放一幅，验完就清掉再放下一幅：16 格见方的测试结构里并排放不下十幅画，
     * 而锚点只要在墙正中，六格宽四格高的 {@code painting_feuer} 也挂得下
     *
     * <p>放下之后不立刻数：实体所在的区块段要先进入可查询状态才看得见
     * （测试环境的区块刻会掉队，见 {@code GameTestChunkWaitMixin}），
     * 所以摆放走一步，计数放进逐 tick 重试的等待里；清掉之后同样等它真的消失
     */
    @GameTest(template = "ddt_empty", timeoutTicks = 400)
    public static void eachItemPlacesItsOwnPainting(GameTestHelper helper) {
        for (int x = 1; x <= 15; x++) {
            for (int y = 1; y <= 15; y++) {
                helper.setBlock(new BlockPos(x, y, 3), Blocks.STONE);
            }
        }

        GameTestSequence sequence = helper.startSequence();
        for (Spec spec : SPECS) {
            sequence = sequence.thenExecute(() -> place(helper, spec))
                    .thenWaitUntil(() -> assertPlaced(helper, spec))
                    .thenExecute(() -> discardPlaced(helper))
                    .thenWaitUntil(() -> assertNonePlaced(helper, spec));
        }
        sequence.thenSucceed();
    }

    /** 用这一幅画自己的物品贴着墙面放一次，断言动作被消费、物品减一 */
    private static void place(GameTestHelper helper, Spec spec) {
        BlockPos clickedAbs = helper.absolutePos(WALL_ANCHOR);
        ItemStack stack = new ItemStack(spec.kind().item().get());
        UseOnContext context = new UseOnContext(
                helper.getLevel(),
                null,
                InteractionHand.MAIN_HAND,
                stack,
                new BlockHitResult(Vec3.atCenterOf(clickedAbs), Direction.SOUTH, clickedAbs, false));

        InteractionResult result = spec.kind().item().get().useOn(context);
        helper.assertTrue(
                result.consumesAction(), "using " + spec.name() + "'s item on a wall must consume the action");
        helper.assertTrue(stack.isEmpty(), "placing " + spec.name() + " must consume the item");
    }

    /** 这一处正好一幅画，且种类、朝向、变体、包围盒都对得上 */
    private static void assertPlaced(GameTestHelper helper, Spec spec) {
        List<GhastPaintingEntity> placed = placed(helper);
        helper.assertTrue(
                placed.size() == 1, "expected exactly one painting for " + spec.name() + ", got " + placed.size());

        GhastPaintingEntity painting = placed.get(0);
        helper.assertTrue(
                painting.kind() == spec.kind(),
                spec.name() + "'s item must place that painting, got "
                        + painting.kind().variant().location());
        helper.assertTrue(
                painting.getDirection() == Direction.SOUTH,
                "painting must face the clicked side, got " + painting.getDirection());
        helper.assertTrue(painting.getVariant().is(spec.kind().variant()), "placed painting must show " + spec.name());
        helper.assertTrue(
                painting.getBoundingBox().getXsize() == (double) spec.width(),
                spec.name() + " must be " + spec.width() + " blocks wide, got "
                        + painting.getBoundingBox().getXsize());
        helper.assertTrue(
                painting.getBoundingBox().getYsize() == (double) spec.height(),
                spec.name() + " must be " + spec.height() + " blocks tall, got "
                        + painting.getBoundingBox().getYsize());
    }

    /** 验完把这幅画清掉，好让下一幅用同一处墙面 */
    private static void discardPlaced(GameTestHelper helper) {
        placed(helper).forEach(GhastPaintingEntity::discard);
    }

    private static void assertNonePlaced(GameTestHelper helper, Spec spec) {
        List<GhastPaintingEntity> placed = placed(helper);
        helper.assertTrue(
                placed.isEmpty(), "the " + spec.name() + " of the previous step must be gone, got " + placed.size());
    }

    /** 墙面那一处附近的画：搜索框给到八格，六格宽四格高的画中心也落得进来 */
    private static List<GhastPaintingEntity> placed(GameTestHelper helper) {
        BlockPos anchorAbs = helper.absolutePos(WALL_ANCHOR).relative(Direction.SOUTH);
        return helper.getLevel()
                .getEntitiesOfClass(
                        GhastPaintingEntity.class, AABB.ofSize(Vec3.atCenterOf(anchorAbs), 8.0D, 8.0D, 8.0D));
    }

    /**
     * 实体类型与变体同名（见 {@code ModEntities#painting}），这里照这个约定从注册表取，
     * 顺带把命名钉住：改了一处忘了另一处，这个测试先失败
     */
    @SuppressWarnings("unchecked")
    private static EntityType<GhastPaintingEntity> entityType(GameTestHelper helper, Spec spec) {
        ResourceKey<EntityType<?>> key =
                ResourceKey.create(Registries.ENTITY_TYPE, spec.kind().variant().location());
        return (EntityType<GhastPaintingEntity>) helper.getLevel()
                .registryAccess()
                .registryOrThrow(Registries.ENTITY_TYPE)
                .getHolderOrThrow(key)
                .value();
    }

    private PaintingTests() {}
}
