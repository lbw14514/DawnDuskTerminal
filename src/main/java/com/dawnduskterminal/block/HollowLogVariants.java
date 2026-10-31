package com.dawnduskterminal.block;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/**
 * 空心原木的状态变体，照暮色森林 {@code twilightforest.enums.HollowLogVariants} 抄来
 *
 * <p>{@link Horizontal} 是横向那块的 {@code variant}：空、苔藓、苔藓带草、积雪、水淹。
 * 苔藓与积雪不是随机长出来的，而是玩家往洞口里塞苔藓毯 / 草 / 雪球塞出来的；
 * 水淹不是放水放出来的，而是方块被放在水里时直接取这一档，所以它和「空」互斥。
 *
 * <p>{@link Climbable} 是可攀爬那块的 {@code variant}：藤、梯子、水淹的梯子。
 */
public final class HollowLogVariants {
    public enum Horizontal implements StringRepresentable {
        EMPTY,
        MOSS,
        MOSS_AND_GRASS,
        SNOW,
        WATERLOGGED;

        @Override
        public String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Climbable implements StringRepresentable {
        VINE,
        LADDER,
        LADDER_WATERLOGGED;

        @Override
        public String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    private HollowLogVariants() {}
}
