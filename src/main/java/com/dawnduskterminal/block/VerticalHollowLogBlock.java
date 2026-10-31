package com.dawnduskterminal.block;

import com.dawnduskterminal.registry.ModTerrainBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 竖向空心原木：立着的空心原木，能从顶上跳进去，四面可以挂藤或梯子
 *
 * <p>照暮色森林的 {@code twilightforest.block.VerticalHollowLogBlock} 移植：碰撞箱比模型大一圈
 * （模型内径 2~14，碰撞内径 1~15），所以人不会卡在筒壁上，能顺着管子滑下去；
 * 往洞里塞藤或梯子会把整块换成 {@link ClimbableHollowLogBlock}，这也是攀爬版的唯一来源。
 */
public class VerticalHollowLogBlock extends Block implements SimpleWaterloggedBlock {
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    private static final VoxelShape HOLLOW_SHAPE =
        Shapes.join(Shapes.block(), Block.box(2.0, 0.0, 2.0, 14.0, 16.0, 14.0), BooleanOp.ONLY_FIRST);
    private static final VoxelShape COLLISION_SHAPE =
        Shapes.join(Shapes.block(), Block.box(1.0, 0.0, 1.0, 15.0, 16.0, 15.0), BooleanOp.ONLY_FIRST);

    public VerticalHollowLogBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.getStateDefinition().any().setValue(WATERLOGGED, false));
    }

    private static boolean isInside(HitResult result, BlockPos pos) {
        Vec3 vec = result.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        return vec.x() >= 0.124 && vec.x() <= 0.876 && vec.z() >= 0.124 && vec.z() <= 0.876;
    }

    /** 点在没有水平朝向的面（顶面、底面）时，梯子挂在玩家背后的那一面，与原版放梯子一致 */
    private static Direction attachFace(BlockHitResult hit, Player player) {
        Direction face = hit.getDirection();
        return face.getAxis().isHorizontal() ? face : player.getDirection().getOpposite();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return HOLLOW_SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return COLLISION_SHAPE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WATERLOGGED);
    }

    @Override
    protected ItemInteractionResult useItemOn(
        ItemStack stack,
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        InteractionHand hand,
        BlockHitResult hit
    ) {
        if (!isInside(hit, pos)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }

        if (stack.is(Blocks.VINE.asItem())) {
            level.setBlock(pos, ModTerrainBlocks.HOLLOW_SKYGATE_PINE_LOG_CLIMBABLE.get().defaultBlockState()
                .setValue(ClimbableHollowLogBlock.VARIANT, HollowLogVariants.Climbable.VINE)
                .setValue(ClimbableHollowLogBlock.FACING, attachFace(hit, player)), 3);
            HorizontalHollowLogBlock.playPlaceSound(Blocks.VINE.defaultBlockState(), level, pos, player);
            stack.consume(1, player);
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }

        if (stack.is(Blocks.LADDER.asItem())) {
            level.setBlock(pos, ModTerrainBlocks.HOLLOW_SKYGATE_PINE_LOG_CLIMBABLE.get().defaultBlockState()
                .setValue(ClimbableHollowLogBlock.VARIANT, state.getValue(WATERLOGGED)
                    ? HollowLogVariants.Climbable.LADDER_WATERLOGGED
                    : HollowLogVariants.Climbable.LADDER)
                .setValue(ClimbableHollowLogBlock.FACING, attachFace(hit, player)), 3);
            HorizontalHollowLogBlock.playPlaceSound(Blocks.LADDER.defaultBlockState(), level, pos, player);
            stack.consume(1, player);
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }

        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(WATERLOGGED,
            context.getLevel().getBlockState(context.getClickedPos()).getFluidState().getType() == Fluids.WATER);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(
        BlockState state,
        Direction facing,
        BlockState neighborState,
        LevelAccessor level,
        BlockPos pos,
        BlockPos neighborPos
    ) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        return super.updateShape(state, facing, neighborState, level, pos, neighborPos);
    }
}
