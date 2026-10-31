package com.dawnduskterminal.block;

import com.dawnduskterminal.registry.ModTerrainBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.ItemAbilities;

/**
 * 挂上藤或梯子的竖向空心原木
 *
 * <p>照暮色森林的 {@code twilightforest.block.ClimbableHollowLogBlock} 移植。
 * {@code facing} 指藤或梯子所贴的那一面，模型里那层薄板就贴在这一面；剪刀把它剪回普通的竖向空心原木，
 * 同时掉回藤或梯子。
 *
 * <p>与暮色森林的差别只有水的表示：那边用一个自定义接口在三种方块之间搬水，
 * 这里直接用 {@code ladder_waterlogged} 这一档表示进水，剪掉梯子时再写回竖向方块的 {@code waterlogged}。
 */
public class ClimbableHollowLogBlock extends HorizontalDirectionalBlock {
    public static final EnumProperty<HollowLogVariants.Climbable> VARIANT =
        EnumProperty.create("variant", HollowLogVariants.Climbable.class);

    private static final VoxelShape LADDER_NORTH = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 3.0);
    private static final VoxelShape LADDER_SOUTH = Block.box(0.0, 0.0, 13.0, 16.0, 16.0, 16.0);
    private static final VoxelShape LADDER_EAST = Block.box(13.0, 0.0, 0.0, 16.0, 16.0, 16.0);
    private static final VoxelShape LADDER_WEST = Block.box(0.0, 0.0, 0.0, 3.0, 16.0, 16.0);
    private static final VoxelShape HOLLOW_SHAPE =
        Shapes.join(Shapes.block(), Block.box(2.0, 0.0, 2.0, 14.0, 16.0, 14.0), BooleanOp.ONLY_FIRST);
    private static final VoxelShape COLLISION_SHAPE =
        Shapes.join(Shapes.block(), Block.box(1.0, 0.0, 1.0, 15.0, 16.0, 15.0), BooleanOp.ONLY_FIRST);

    public ClimbableHollowLogBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.getStateDefinition().any()
            .setValue(VARIANT, HollowLogVariants.Climbable.VINE)
            .setValue(FACING, Direction.NORTH));
    }

    private static boolean isInside(HitResult result, BlockPos pos) {
        Vec3 vec = result.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        return vec.x() >= 0.124 && vec.x() <= 0.876 && vec.z() >= 0.124 && vec.z() <= 0.876;
    }

    private VoxelShape withLadder(VoxelShape base, BlockState state) {
        if (state.getValue(VARIANT) == HollowLogVariants.Climbable.VINE) {
            return base;
        }

        return Shapes.or(base, switch (state.getValue(FACING)) {
            case SOUTH -> LADDER_SOUTH;
            case EAST -> LADDER_EAST;
            case WEST -> LADDER_WEST;
            default -> LADDER_NORTH;
        });
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.withLadder(HOLLOW_SHAPE, state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.withLadder(COLLISION_SHAPE, state);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(ClimbableHollowLogBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(VARIANT, FACING);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(VARIANT) == HollowLogVariants.Climbable.LADDER_WATERLOGGED
            ? Fluids.WATER.getSource(false)
            : super.getFluidState(state);
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
        if (state.getValue(VARIANT) == HollowLogVariants.Climbable.LADDER_WATERLOGGED) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        return super.updateShape(state, facing, neighborState, level, pos, neighborPos);
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
        if (!isInside(hit, pos) || !stack.canPerformAction(ItemAbilities.SHEARS_HARVEST)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }

        HollowLogVariants.Climbable variant = state.getValue(VARIANT);
        level.setBlock(pos, ModTerrainBlocks.HOLLOW_SKYGATE_PINE_LOG_VERTICAL.get().defaultBlockState()
            .setValue(VerticalHollowLogBlock.WATERLOGGED, variant == HollowLogVariants.Climbable.LADDER_WATERLOGGED), 3);
        level.playSound(null, pos, SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 1.0F, 1.0F);
        if (!player.isCreative()) {
            stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                new ItemStack(variant == HollowLogVariants.Climbable.VINE ? Items.VINE : Items.LADDER)));
        }

        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }
}
