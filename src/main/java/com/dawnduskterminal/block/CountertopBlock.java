package com.dawnduskterminal.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 「台面」方块：黑木台面与去皮黑木台面共用这一类
 *
 * <p>行为上就是一块普通整方块，类本身只标记一件事——顶面的 47 格连接纹理要认出「哪些方块算台面」：
 * {@link com.dawnduskterminal.client.model.CountertopModel} 判邻居用的是 {@link #isCountertop}，
 * 两种台面共用同一张顶面，于是彼此也接得上，一行台面的顶面能连成一片
 *
 * <p>判定写成静态方法而不是「两块方块进一个集合」：那样要么在静态初始化期就拿 {@code DeferredBlock} 的
 * 值（注册顺序一变就炸），要么每次渲染都查一遍注册表。{@code instanceof} 两样都不碰
 */
public class CountertopBlock extends Block {
    public CountertopBlock(Properties properties) {
        super(properties);
    }

    /** 这个状态算不算一块台面。顶面连接判定与以后可能有的「台面上放东西」都走这一条 */
    public static boolean isCountertop(BlockState state) {
        return state.getBlock() instanceof CountertopBlock;
    }
}
