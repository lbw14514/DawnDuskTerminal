package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.block.entity.Experiment004BlockEntity;
import com.dawnduskterminal.block.entity.Experiment051BlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 方块实体类型。目前有试验物品 004 那台熔炉与 051 那台机器 */
public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, DawnDuskTerminal.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<Experiment004BlockEntity>> EXPERIMENT_004 =
        BLOCK_ENTITIES.register("experiment_004",
            () -> BlockEntityType.Builder.of(Experiment004BlockEntity::new, ModBlocks.EXPERIMENT_004.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<Experiment051BlockEntity>> EXPERIMENT_051 =
        BLOCK_ENTITIES.register("experiment_051",
            () -> BlockEntityType.Builder.of(Experiment051BlockEntity::new, ModBlocks.EXPERIMENT_051.get()).build(null));

    private ModBlockEntities() {}

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }
}
