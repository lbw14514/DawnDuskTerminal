package com.dawnduskterminal.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
import twilightforest.init.TFBlocks;

/**
 * 横向空心原木：一根躺着的原木，中间被掏空，玩家能从一端钻进去
 *
 * <p>照暮色森林的 {@code twilightforest.block.HorizontalHollowLogBlock} 移植，行为逐条对齐：
 * <ul>
 *   <li>洞口在两端，剖面上是「口」字形的四壁；{@code variant} 决定洞里塞了什么
 *   <li>往洞里塞苔藓毯（{@code variant=moss}）、再塞草（{@code moss_and_grass}）、塞雪球（{@code snow}）；
 *       铁锹铲掉积雪、剪刀剪下苔藓与草，都会把方块退回 {@code empty}
 *   <li>方块被放在水里时直接取 {@code waterlogged} 这一档，没有单独的 {@code waterlogged} 布尔属性
 *   <li>潜行着朝洞口方向、贴着洞口点时，玩家会切成游泳姿势，方便爬进去
 * </ul>
 *
 * <p>苔藓用的是暮色森林的 {@code moss_patch}（贴图取 {@code twilightforest:block/mosspatch}
 * 与 {@code moss_overhang}），与暮色森林自己的空心原木完全一致。
 */
public class HorizontalHollowLogBlock extends Block {
    public static final EnumProperty<Direction.Axis> HORIZONTAL_AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final EnumProperty<HollowLogVariants.Horizontal> VARIANT =
        EnumProperty.create("variant", HollowLogVariants.Horizontal.class);

    private static final VoxelShape HOLLOW_SHAPE_X =
        Shapes.join(Shapes.block(), Block.box(0.0, 2.0, 2.0, 16.0, 14.0, 14.0), BooleanOp.ONLY_FIRST);
    private static final VoxelShape HOLLOW_SHAPE_Z =
        Shapes.join(Shapes.block(), Block.box(2.0, 2.0, 0.0, 14.0, 14.0, 16.0), BooleanOp.ONLY_FIRST);
    private static final VoxelShape CARPET_SHAPE_X =
        Shapes.join(Shapes.block(), Block.box(0.0, 3.0, 2.0, 16.0, 14.0, 14.0), BooleanOp.ONLY_FIRST);
    private static final VoxelShape CARPET_SHAPE_Z =
        Shapes.join(Shapes.block(), Block.box(2.0, 3.0, 0.0, 14.0, 14.0, 16.0), BooleanOp.ONLY_FIRST);
    private static final VoxelShape COLLISION_SHAPE_X =
        Shapes.join(Shapes.block(), Block.box(0.0, 1.0, 1.0, 16.0, 15.0, 15.0), BooleanOp.ONLY_FIRST);
    private static final VoxelShape COLLISION_SHAPE_Z =
        Shapes.join(Shapes.block(), Block.box(1.0, 1.0, 0.0, 15.0, 15.0, 16.0), BooleanOp.ONLY_FIRST);

    public HorizontalHollowLogBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.getStateDefinition().any()
            .setValue(HORIZONTAL_AXIS, Direction.Axis.X)
            .setValue(VARIANT, HollowLogVariants.Horizontal.EMPTY));
    }

    /** 洞里塞的东西只能在「空」与「没进水的洞」上换；两端有水时不允许盖住 */
    private static boolean canChangeVariant(HollowLogVariants.Horizontal variant, Level level, BlockPos pos, Direction.Axis axis) {
        return variant == HollowLogVariants.Horizontal.EMPTY
            || variant == HollowLogVariants.Horizontal.WATERLOGGED
                && level.getFluidState(pos.relative(Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE))).getType() != Fluids.WATER
                && level.getFluidState(pos.relative(Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE))).getType() != Fluids.WATER;
    }

    /** 点击位置是否落在洞口那一块，只有点在洞里才算「往洞里塞东西」 */
    private static boolean isInside(HitResult result, Direction.Axis axis, BlockPos pos) {
        Vec3 vec = result.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        if (vec.y() < 0.124 || vec.y() > 0.876) {
            return false;
        }

        return switch (axis) {
            case X -> vec.z() >= 0.124 && vec.z() <= 0.876;
            case Z -> vec.x() >= 0.124 && vec.x() <= 0.876;
            default -> false;
        };
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        boolean flat = switch (state.getValue(VARIANT)) {
            case EMPTY, WATERLOGGED -> true;
            default -> false;
        };

        if (state.getValue(HORIZONTAL_AXIS) == Direction.Axis.X) {
            return flat ? HOLLOW_SHAPE_X : CARPET_SHAPE_X;
        }

        return flat ? HOLLOW_SHAPE_Z : CARPET_SHAPE_Z;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(HORIZONTAL_AXIS) == Direction.Axis.X) {
            return COLLISION_SHAPE_X;
        }

        return COLLISION_SHAPE_Z;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HORIZONTAL_AXIS, VARIANT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
            .setValue(HORIZONTAL_AXIS, context.getClickedFace().getAxis())
            .setValue(VARIANT, context.getLevel().getBlockState(context.getClickedPos()).getFluidState().getType() == Fluids.WATER
                ? HollowLogVariants.Horizontal.WATERLOGGED
                : HollowLogVariants.Horizontal.EMPTY);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(VARIANT) == HollowLogVariants.Horizontal.WATERLOGGED
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
        if (state.getValue(VARIANT) == HollowLogVariants.Horizontal.WATERLOGGED) {
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
        Direction.Axis axis = state.getValue(HORIZONTAL_AXIS);
        if (!isInside(hit, axis, pos)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }

        HollowLogVariants.Horizontal variant = state.getValue(VARIANT);

        if (stack.is(TFBlocks.MOSS_PATCH.get().asItem())) {
            if (canChangeVariant(variant, level, pos, axis)) {
                level.setBlock(pos, state.setValue(VARIANT, HollowLogVariants.Horizontal.MOSS), 3);
                playPlaceSound(TFBlocks.MOSS_PATCH.get().defaultBlockState(), level, pos, player);
                stack.consume(1, player);
                return ItemInteractionResult.sidedSuccess(level.isClientSide());
            }
        } else if (stack.is(Blocks.SHORT_GRASS.asItem())) {
            if (variant == HollowLogVariants.Horizontal.MOSS) {
                level.setBlock(pos, state.setValue(VARIANT, HollowLogVariants.Horizontal.MOSS_AND_GRASS), 3);
                playPlaceSound(Blocks.SHORT_GRASS.defaultBlockState(), level, pos, player);
                stack.consume(1, player);
                return ItemInteractionResult.sidedSuccess(level.isClientSide());
            }
        } else if (stack.is(Items.SNOWBALL)) {
            if (canChangeVariant(variant, level, pos, axis)) {
                level.setBlock(pos, state.setValue(VARIANT, HollowLogVariants.Horizontal.SNOW), 3);
                playPlaceSound(Blocks.SNOW.defaultBlockState(), level, pos, player);
                stack.consume(1, player);
                return ItemInteractionResult.sidedSuccess(level.isClientSide());
            }
        } else if (stack.canPerformAction(ItemAbilities.SHOVEL_DIG)) {
            if (variant == HollowLogVariants.Horizontal.SNOW) {
                level.setBlock(pos, state.setValue(VARIANT, HollowLogVariants.Horizontal.EMPTY), 3);
                playBreakSound(Blocks.SNOW.defaultBlockState(), level, pos, player);
                if (!player.isCreative()) {
                    stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
                    level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, new ItemStack(Items.SNOWBALL)));
                }

                return ItemInteractionResult.sidedSuccess(level.isClientSide());
            }
        } else if (stack.canPerformAction(ItemAbilities.SHEARS_HARVEST)
            && (variant == HollowLogVariants.Horizontal.MOSS || variant == HollowLogVariants.Horizontal.MOSS_AND_GRASS)) {
            level.setBlock(pos, state.setValue(VARIANT, HollowLogVariants.Horizontal.EMPTY), 3);
            level.playSound(null, pos, SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 1.0F, 1.0F);
            if (!player.isCreative()) {
                stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
                level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    new ItemStack(TFBlocks.MOSS_PATCH.get())));
                if (variant == HollowLogVariants.Horizontal.MOSS_AND_GRASS) {
                    level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        new ItemStack(Blocks.SHORT_GRASS)));
                }
            }

            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }

        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    /** 潜行着点洞口、且人和洞口在同一条轴上时切成游泳姿势，好钻进去 */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        Direction.Axis axis = state.getValue(HORIZONTAL_AXIS);
        if (isInside(hit, axis, pos)
            && player.isSecondaryUseActive()
            && player.getDirection().getAxis() == axis
            && player.blockPosition().getY() == pos.getY()
            && !player.isFallFlying()
            && player.position().distanceTo(Vec3.atBottomCenterOf(pos)) < 0.81) {
            player.setPose(Pose.SWIMMING);
        }

        return super.useWithoutItem(state, level, pos, player, hit);
    }

    public static void playPlaceSound(BlockState state, Level level, BlockPos pos, Player player) {
        SoundType sound = state.getSoundType(level, pos, player);
        level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
    }

    public static void playBreakSound(BlockState state, Level level, BlockPos pos, Player player) {
        SoundType sound = state.getSoundType(level, pos, player);
        level.playSound(null, pos, sound.getBreakSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
    }
}
