package com.dawnduskterminal.geckolib3.core.molang.builtin.math;

import com.dawnduskterminal.geckolib3.util.Interpolations;
import com.dawnduskterminal.molang.runtime.ExecutionContext;
import com.dawnduskterminal.molang.runtime.Function;

public class Lerp implements Function {
    @Override
    public Object evaluate(ExecutionContext<?> context, ArgumentCollection arguments) {
        return Interpolations.lerp(arguments.getAsDouble(context, 0),
                arguments.getAsDouble(context, 1),
                arguments.getAsDouble(context, 2));
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size == 3;
    }
}
