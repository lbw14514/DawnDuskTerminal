package com.dawnduskterminal.geckolib3.core.molang.builtin.math;

import com.dawnduskterminal.molang.runtime.ExecutionContext;
import com.dawnduskterminal.molang.runtime.Function;

public class Pow implements Function {
    @Override
    public Object evaluate(ExecutionContext<?> context, ArgumentCollection arguments) {
        return Math.pow(arguments.getAsDouble(context, 0),
                arguments.getAsDouble(context, 1));
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size == 2;
    }
}
