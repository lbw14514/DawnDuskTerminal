package com.dawnduskterminal.geckolib3.core.molang.builtin.math;

import com.dawnduskterminal.molang.runtime.ExecutionContext;
import com.dawnduskterminal.molang.runtime.Function;

public class Sin implements Function {
    @Override
    public Object evaluate(ExecutionContext<?> context, ArgumentCollection arguments) {
        return Math.sin(arguments.getAsDouble(context, 0) / 180 * Math.PI);
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size == 1;
    }
}
