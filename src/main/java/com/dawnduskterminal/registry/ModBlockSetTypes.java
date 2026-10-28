package com.dawnduskterminal.registry;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.BlockSetType;

/**
 * 方块组（{@code BlockSetType}）：木与金属各一档给门、活板门、压力板、按钮共用一组参数
 *
 * <p>它不在注册表里（1.21.1 没有 {@code BLOCK_SET_TYPE} 这个注册表），原版的十几档也是在一个静态块里
 * {@code new} 出来再 {@link BlockSetType#register} 进一张按名字索引的表里的，所以这里照同样的做法做
 *
 * <p>{@link #WROUGHT_IRON_GLASS} 是锻铁玻璃活板门用的那一档：
 * <ul>
 *   <li>{@code canOpenByHand} 是 {@code true} —— 空手点得开。原版铁活板门那档是 {@code false}，
 *       只吃红石，用它就会出现「活板门点不动」这种事故
 *   <li>开合声取原版铁活板门那两声（玻璃没有自己的门声），踩踏 破坏 落地的音效取 {@link SoundType#GLASS}
 *   <li>压力板与按钮那六声照原版铁那一档填：本模组暂时没有压力板与按钮，但构造器要求给齐
 * </ul>
 */
public final class ModBlockSetTypes {
    /** 锻铁玻璃那一档：能空手开、玻璃音效、铁活板门的开合声 */
    public static final BlockSetType WROUGHT_IRON_GLASS = BlockSetType.register(new BlockSetType(
        "wrought_iron_glass",
        true,
        true,
        false,
        BlockSetType.PressurePlateSensitivity.EVERYTHING,
        SoundType.GLASS,
        SoundEvents.IRON_DOOR_CLOSE,
        SoundEvents.IRON_DOOR_OPEN,
        SoundEvents.IRON_TRAPDOOR_CLOSE,
        SoundEvents.IRON_TRAPDOOR_OPEN,
        SoundEvents.METAL_PRESSURE_PLATE_CLICK_OFF,
        SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON,
        SoundEvents.STONE_BUTTON_CLICK_OFF,
        SoundEvents.STONE_BUTTON_CLICK_ON));

    private ModBlockSetTypes() {}
}
