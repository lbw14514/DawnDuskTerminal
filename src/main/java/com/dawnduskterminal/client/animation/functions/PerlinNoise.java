package com.dawnduskterminal.client.animation.functions;

import com.dawnduskterminal.client.animation.YsmNoise;
import com.dawnduskterminal.geckolib3.core.molang.context.IContext;
import com.dawnduskterminal.geckolib3.core.molang.function.entity.EntityFunction;
import com.dawnduskterminal.molang.runtime.ExecutionContext;
import net.minecraft.world.entity.Entity;

/**
 * {@code ysm.perlin_noise(seed, x, y, z)}
 *
 * <p>算法与坐标透传的理由见 {@link YsmNoise}。
 */
public class PerlinNoise extends EntityFunction {
    /**
     * YSM 允许省略后面的坐标：{@code perlin_noise(seed, x)} 等于 y、z 取 0
     *
     * <p>这套模型包正是这么用的（发丝那条写的是 {@code perlin_noise(1492, query.life_time)}），
     * 只接受四个参数会让该通道整条解析失败，于是发丝不动。
     */
    @Override
    protected Object eval(ExecutionContext<IContext<Entity>> context, ArgumentCollection arguments) {
        int seed = arguments.getAsInt(context, 0);
        float x = arguments.getAsFloat(context, 1);
        float y = arguments.size() > 2 ? arguments.getAsFloat(context, 2) : 0.0F;
        float z = arguments.size() > 3 ? arguments.getAsFloat(context, 3) : 0.0F;
        return YsmNoise.perlin(seed, x, y, z);
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size >= 2 && size <= 4;
    }
}
