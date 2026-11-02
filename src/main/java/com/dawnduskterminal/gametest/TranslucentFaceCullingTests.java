package com.dawnduskterminal.gametest;

import com.dawnduskterminal.registry.ModBlocks;
import com.dawnduskterminal.registry.ModTerrainBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 钉住两种半透明苍穹方块与传送门流体的面剔除
 *
 * <p>两个相邻半透明方块不能画它们之间的那个面。这个剔除来自
 * {@link net.minecraft.world.level.block.state.BlockState#skipRendering}，{@link Block#shouldRenderFace}
 * 会最先问它（{@code ModelBlockRenderer} 在构建区块网格时问的正是这个问题）。这是半透明方块唯一可用
 * 的机制，因为 {@code shouldRenderFace} 再往下的体素形状分支被 {@code neighbour.canOcclude()} 挡着，
 * 而半透明整方块必须是 noOcclusion。原版玻璃与冰靠 {@code HalfTransparentBlock} 回答它，这就是苍穹方块
 * 注册成那个类而不是普通 {@link Block} 的原因，而这些测试就是用来钉住这一点的
 *
 * <p>流体那一半是同一个想法再低一层：{@code LiquidBlockRenderer} 对每个流体面问
 * {@code BlockState#shouldHideAdjacentFluidFace}，它的默认行为是当邻居方块携带同一种流体时隐藏该面
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class TranslucentFaceCullingTests {
    private static final BlockPos A = new BlockPos(1, 2, 1);
    private static final BlockPos B = new BlockPos(2, 2, 1);
    private static final Direction A_TO_B = Direction.EAST;

    /** 区块网格构建器对 A 朝向 B 的那个面所问的正是这个问题 */
    private static boolean faceOfARendered(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos a = helper.absolutePos(A);
        BlockPos b = helper.absolutePos(B);
        return Block.shouldRenderFace(level.getBlockState(a), level, a, A_TO_B, b);
    }

    @GameTest(template = "ddt_empty")
    public static void translucentCanopyCullsTheFaceBetweenTwoOfItself(GameTestHelper helper) {
        for (Block canopy : new Block[] {ModTerrainBlocks.FROZEN_CANOPY.get(), ModTerrainBlocks.FRAGILE_CANOPY.get()}) {
            helper.setBlock(A, canopy);
            helper.setBlock(B, canopy);
            helper.assertFalse(
                    faceOfARendered(helper),
                    "two adjacent " + canopy.getName().getString()
                            + " must cull the face they share, otherwise the inside of a canopy volume shows as a lattice");
        }
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void cullingAgreesWithTheVanillaReference(GameTestHelper helper) {
        // 原版冰与 frozen_canopy 是同一个 HalfTransparentBlock 形态
        helper.setBlock(A, Blocks.ICE);
        helper.setBlock(B, Blocks.ICE);
        helper.assertFalse(faceOfARendered(helper), "vanilla ice must cull the face shared with another ice");

        // 对照组：如果一对不透明方块不剔除，上面的断言就证明不了任何东西
        helper.setBlock(A, Blocks.STONE);
        helper.setBlock(B, Blocks.STONE);
        helper.assertFalse(faceOfARendered(helper), "two stones must cull the face between them");

        // 朝向半透明方块的那个不透明面必须保留，否则它背后的墙会透出一个洞
        helper.setBlock(A, Blocks.STONE);
        helper.setBlock(B, ModTerrainBlocks.FROZEN_CANOPY.get());
        helper.assertTrue(
                faceOfARendered(helper), "the stone face pointing into a translucent canopy block must be drawn");
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void differentBlocksAreNotCulledAgainstEachOther(GameTestHelper helper) {
        helper.setBlock(A, ModTerrainBlocks.FROZEN_CANOPY.get());
        helper.setBlock(B, ModTerrainBlocks.FRAGILE_CANOPY.get());
        helper.assertTrue(
                faceOfARendered(helper),
                "the two canopy blocks are different blocks; vanilla culls a block only against itself, so this face stays");
        helper.succeed();
    }

    @GameTest(template = "ddt_empty")
    public static void portalFluidCullsTheFaceBetweenTwoOfItself(GameTestHelper helper) {
        // 放置方式与 FluidReactionTests 相同：用普通默认状态，同一 tick 内读回
        helper.setBlock(A, ModBlocks.PORTAL_FLUID.get());
        helper.setBlock(B, ModBlocks.PORTAL_FLUID.get());
        ServerLevel level = helper.getLevel();
        BlockPos a = helper.absolutePos(A);
        BlockPos b = helper.absolutePos(B);
        // WEST 是 B 贴着 A 的那个面。该方法在「这个流体面不该被渲染」时返回 true，而 A 携带同一种流体，
        // 所以两个方块会像水一样剔除它们之间的液面
        helper.assertTrue(
                level.getBlockState(b).shouldHideAdjacentFluidFace(A_TO_B.getOpposite(), level.getFluidState(a)),
                "two portal fluid blocks must hide the fluid surface between them, exactly like water does");
        // 最后别留下任何还会持续 tick（因而不断弄脏测试世界）的东西
        helper.setBlock(A, Blocks.AIR);
        helper.setBlock(B, Blocks.AIR);
        helper.succeed();
    }
}
