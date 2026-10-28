package com.dawnduskterminal.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 锻铁玻璃方块：与原版玻璃同一支 {@link TransparentBlock}，只多一条「与锻铁玻璃活板门贴合的面也剔掉」
 *
 * <p>原版 {@code HalfTransparentBlock#skipRendering} 只剔除**同一种方块**之间的内部面，所以
 * 一块玻璃上面压着一块关着的活板门时，玻璃的顶面与活板门的底面会各画一遍——两张都是半透明的边框，
 * 叠起来就是一条粗边加一条内影。这里把「活板门整面贴在我这一面上」的情形也算进剔除里
 *
 * <p>只认「整面贴住」的那两种：关着的活板门压在我顶上（它的板底正好是我的顶面）、或者关着的活板门
 * 挂在我下面（它的板顶正好是我的底面）。竖着的（打开的）活板门只有 16x3 的边贴着，盖不满整面，
 * 剔掉会留下洞，所以不剔
 */
public class WroughtIronGlassBlock extends TransparentBlock {
    public WroughtIronGlassBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacentState, Direction side) {
        if (super.skipRendering(state, adjacentState, side)) {
            return true;
        }
        // 相邻那格是锻铁玻璃活板门，且它朝我这里的那一面整面贴住
        return WroughtIronGlassTrapdoorBlock.closedPlateFace(adjacentState, side.getOpposite());
    }
}
