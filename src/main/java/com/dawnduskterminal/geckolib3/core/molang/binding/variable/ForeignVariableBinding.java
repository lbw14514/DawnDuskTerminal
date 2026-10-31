package com.dawnduskterminal.geckolib3.core.molang.binding.variable;

import com.dawnduskterminal.geckolib3.core.molang.context.IContext;
import com.dawnduskterminal.geckolib3.core.molang.storage.IForeignVariableStorage;
import com.dawnduskterminal.geckolib3.core.molang.util.StringPool;
import com.dawnduskterminal.molang.runtime.ExecutionContext;
import com.dawnduskterminal.molang.runtime.Variable;
import com.dawnduskterminal.molang.runtime.binding.ObjectBinding;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("MapOrSetKeyShouldOverrideHashCodeEquals")
public class ForeignVariableBinding implements ObjectBinding {
    private final Int2ReferenceOpenHashMap<ForeignVariable> variableMap = new Int2ReferenceOpenHashMap<>();

    @Override
    public Object getProperty(String name) {
        return variableMap.computeIfAbsent(StringPool.computeIfAbsent(name), ForeignVariable::new);
    }

    public void reset() {
        variableMap.clear();
    }

    private record ForeignVariable(int name) implements Variable {
        @Override
        @SuppressWarnings("unchecked")
        public Object evaluate(final @NotNull ExecutionContext<?> context) {
            IForeignVariableStorage storage = ((IContext<Object>) context.entity()).foreignStorage();
            if (storage != null) {
                return storage.getPublic(name);
            } else {
                return null;
            }
        }
    }
}
