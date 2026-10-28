package com.dawnduskterminal.block;

import com.dawnduskterminal.registry.ModBlockSetTypes;
import com.dawnduskterminal.registry.ModTerrainBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * 锻铁玻璃的活板门：两块 16x16 的大面用九宫图（由 {@code WroughtIronGlassModel} 按「这个面是哪一面」裁格），
 * 四条薄边用 {@code block/wrought_iron_glass_particle} 的前几行像素
 *
 * <p>状态、开合、红石、含水全走原版 {@link TrapDoorBlock}；方块组用
 * {@link ModBlockSetTypes#WROUGHT_IRON_GLASS}（空手点得开、玻璃音效、铁活板门的开合声）
 *
 * <p>内部面剔除：原版活板门是实心贴图，两面挨着只是多画一层；这里是半透明的，两个活板门贴在一起、
 * 或者活板门压在锻铁玻璃上，会看到两条边框叠在一起。所以 {@link #skipRendering} 把「两块板子真的贴住」
 * 的那些面剔掉，规则见 {@link #sameTypePlatesTouch}——只剔板面完全重合的方向，
 * 上下错开（比如一块下半板搭在另一块上面、中间隔着空气）的那一面照常画
 */
public class WroughtIronGlassTrapdoorBlock extends TrapDoorBlock {
    public WroughtIronGlassTrapdoorBlock(BlockBehaviour.Properties properties) {
        super(ModBlockSetTypes.WROUGHT_IRON_GLASS, properties);
    }

    @Override
    public MapCodec<? extends TrapDoorBlock> codec() {
        return simpleCodec(WroughtIronGlassTrapdoorBlock::new);
    }

    /**
     * 剔掉与同类活板门、与锻铁玻璃方块之间重合的内部面
     *
     * <p>这类判定只决定「这一面画不画」，不影响状态与碰撞
     */
    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacentState, Direction side) {
        if (adjacentState.is(this)) {
            return sameTypePlatesTouch(state, adjacentState, side);
        }
        if (adjacentState.is(ModTerrainBlocks.WROUGHT_IRON_GLASS.get())) {
            // 关着的板子整面贴住玻璃：下半板贴底下那块，上半板贴上面那块
            return closedPlateFace(state, side);
        }
        return super.skipRendering(state, adjacentState, side);
    }

    /**
     * 两块同类活板门在 {@code side} 这一方向上的面是否完全重合
     *
     * <p>关着的板子：同一半（都在下半格或都在上半格）才齐平，且只能是水平方向——板子是 16x3 的薄片，
     * 上下两块之间隔着空气，剔了就是洞
     *
     * <p>打开的板子：板面是竖的，只有面向相同、且方向与板面法线垂直的那两侧，边缘才真正贴住
     */
    private static boolean sameTypePlatesTouch(BlockState state, BlockState adjacentState, Direction side) {
        boolean open = state.getValue(OPEN);
        if (open != adjacentState.getValue(OPEN)) {
            return false;
        }
        if (open) {
            Direction facing = state.getValue(FACING);
            return facing == adjacentState.getValue(FACING) && side.getAxis() != facing.getAxis();
        }
        return state.getValue(HALF) == adjacentState.getValue(HALF) && side.getAxis().isHorizontal();
    }

    /**
     * 这块活板门朝 {@code side} 的那一面是否整面贴住相邻那一格（只对关着的板子成立）
     *
     * <p>下半格的板子占 0~3，所以它的底面正好落在 y=0；上半格占 13~16，顶面正好落在 y=16。
     * 竖着的板子只占三条边，盖不满整面，一律回答 {@code false}
     */
    public static boolean closedPlateFace(BlockState state, Direction side) {
        if (!state.is(ModTerrainBlocks.WROUGHT_IRON_GLASS_TRAPDOOR.get()) || state.getValue(OPEN)) {
            return false;
        }
        Half half = state.getValue(HALF);
        return side == Direction.DOWN ? half == Half.BOTTOM : side == Direction.UP && half == Half.TOP;
    }
}
