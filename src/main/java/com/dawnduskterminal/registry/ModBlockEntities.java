package com.dawnduskterminal.registry;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.block.entity.E115RegeneratorBlockEntity;
import com.dawnduskterminal.block.entity.Experiment004BlockEntity;
import com.dawnduskterminal.block.entity.Experiment051BlockEntity;
import com.dawnduskterminal.block.entity.Experiment065BlockEntity;
import com.dawnduskterminal.block.entity.Experiment069BlockEntity;
import com.dawnduskterminal.block.entity.Experiment076BlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 方块实体类型。目前有试验物品 004 那台熔炉、051 那台机器、065 那台不让人打伤害的机器、069 那台让更新不处理的机器、
 * 076 那个没有数据、只为挂客户端渲染器的异常方块，以及 115 再生机那一格产物格加一个进度
 */
public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, DawnDuskTerminal.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<Experiment004BlockEntity>> EXPERIMENT_004 =
        BLOCK_ENTITIES.register("experiment_004",
            () -> BlockEntityType.Builder.of(Experiment004BlockEntity::new, ModBlocks.EXPERIMENT_004.get()).build(null));

    /** 115 再生机：一格产物格加一个攒着的进度，见 {@link E115RegeneratorBlockEntity} */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<E115RegeneratorBlockEntity>> E_115_REGENERATOR =
        BLOCK_ENTITIES.register("e_115_regenerator",
            () -> BlockEntityType.Builder.of(E115RegeneratorBlockEntity::new, ModBlocks.E_115_REGENERATOR.get())
                .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<Experiment051BlockEntity>> EXPERIMENT_051 =
        BLOCK_ENTITIES.register("experiment_051",
            () -> BlockEntityType.Builder.of(Experiment051BlockEntity::new, ModBlocks.EXPERIMENT_051.get()).build(null));

    /**
     * 试验物品 065 的方块实体：没有数据，只为在区块载入/卸载时登记与撤掉那张场的场心，
     * 见 {@link Experiment065BlockEntity}
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<Experiment065BlockEntity>> EXPERIMENT_065 =
        BLOCK_ENTITIES.register("experiment_065",
            () -> BlockEntityType.Builder.of(Experiment065BlockEntity::new, ModBlocks.EXPERIMENT_065.get()).build(null));

    /** 试验物品 069 的方块实体：同样只为登记「更新不处理」那张场的场心，见 {@link Experiment069BlockEntity} */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<Experiment069BlockEntity>> EXPERIMENT_069 =
        BLOCK_ENTITIES.register("experiment_069",
            () -> BlockEntityType.Builder.of(Experiment069BlockEntity::new, ModBlocks.EXPERIMENT_069.get()).build(null));

    /**
     * 试验物品 076 的方块实体：没有任何数据，只为让客户端的 {@code Experiment076Renderer} 有个落点。
     * 那个方块的外观每 tick 现算，见 {@link Experiment076BlockEntity}
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<Experiment076BlockEntity>> EXPERIMENT_076 =
        BLOCK_ENTITIES.register("experiment_076",
            () -> BlockEntityType.Builder.of(Experiment076BlockEntity::new, ModBlocks.EXPERIMENT_076.get()).build(null));

    private ModBlockEntities() {}

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }
}
