package com.dawnduskterminal.client.animation.functions;

import com.dawnduskterminal.client.animation.struct.Vec3fStruct;
import com.dawnduskterminal.geckolib3.core.molang.context.IContext;
import com.dawnduskterminal.geckolib3.core.molang.function.entity.EntityFunction;
import com.dawnduskterminal.geckolib3.core.processor.IBone;
import com.dawnduskterminal.molang.runtime.ExecutionContext;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;

public abstract class BoneParamFunction extends EntityFunction {
    @Override
    public boolean validateArgumentSize(int size) {
        return size == 1;
    }

    @Override
    protected Object eval(ExecutionContext<IContext<Entity>> context, ArgumentCollection arguments) {
        var str = arguments.getAsString(context, 0);
        if (StringUtil.isNullOrEmpty(str)) {
            return null;
        }

        var bone = context.entity().geoInstance().getAnimationProcessor().getBone(str);
        if (bone == null) {
            return null;
        }

        return getParam(bone);
    }

    protected abstract Vec3fStruct getParam(@NotNull IBone bone);
}
