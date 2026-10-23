package com.dawnduskterminal.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 试验物品 019 摆下来的样子：一丛植物
 *
 * <p>挂 {@link BushBlock} 这一层就是为了植物那一套判定：只能站在 {@code #minecraft:dirt}（草方块、泥土、
 * 灰化土、菌丝、苔藓一类）或者耕地上，底下的方块没了自己消失，走路不挡人，走着不减视野，
 * 这几条都是 {@code BushBlock} 写的，这里一条都不重写
 *
 * <p>形状按贴图给一圈 2..14 的包围盒（原版花是 5..11，这丛叶子铺得更开），再叠上
 * {@code offsetType(XZ)} 给随机偏移——同一丛植物每次摆位置都不一样，与原版草、花一致
 *
 * <p>方块贴图直接用物品那张（{@code dawnduskterminal:item/experiment_019}）：物品图标画的就是这丛植物，
 * 摆下来要看的是同一张图，另存一份 {@code block/experiment_019.png} 只会让两张图日后跑偏
 *
 * <p>模型 {@code models/block/experiment_019.json} 是 {@code minecraft:block/cross} 加
 * {@code "render_type": "minecraft:cutout"}。后一个字段不是可有可无的：方块模型默认渲染层是 solid，
 * 贴图上的透明像素会被涂成黑底，整丛植物就变成一块黑方块。原版花是在代码里
 * {@code ItemBlockRenderTypes.setRenderLayer} 干的，那个入口 1.19 起就废弃了，改成在模型 JSON 里声明，
 * NeoForge 由 {@code ExtendedBlockModelDeserializer} 读这个字段
 */
public class Experiment019Block extends BushBlock {
    public static final MapCodec<Experiment019Block> CODEC = simpleCodec(Experiment019Block::new);

    private static final VoxelShape SHAPE = box(2.0D, 0.0D, 2.0D, 14.0D, 13.0D, 14.0D);

    public Experiment019Block(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Vec3 offset = state.getOffset(level, pos);
        return SHAPE.move(offset.x, offset.y, offset.z);
    }
}
