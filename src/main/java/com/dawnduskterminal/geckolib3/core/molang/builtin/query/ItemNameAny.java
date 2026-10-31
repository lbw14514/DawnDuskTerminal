package com.dawnduskterminal.geckolib3.core.molang.builtin.query;

import com.dawnduskterminal.geckolib3.core.molang.context.IContext;
import com.dawnduskterminal.geckolib3.core.molang.function.entity.LivingEntityFunction;
import com.dawnduskterminal.geckolib3.util.MolangUtils;
import com.dawnduskterminal.molang.runtime.ExecutionContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public class ItemNameAny extends LivingEntityFunction {
    @Override
    protected Object eval(ExecutionContext<IContext<LivingEntity>> context, ArgumentCollection arguments) {
        EquipmentSlot slotType = MolangUtils.parseSlotType(context.entity(), arguments.getAsString(context, 0));
        if (slotType == null) {
            return null;
        }

        ItemStack itemStack = context.entity().entity().getItemBySlot(slotType);
        if(itemStack.isEmpty()) {
            return false;
        }

        ResourceLocation actualId = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
        if(actualId == BuiltInRegistries.ITEM.getDefaultKey()) {
            return false;
        }

        for (int i = 1; i < arguments.size(); i++) {
            ResourceLocation id = MolangUtils.parseResourceLocation(context.entity(), arguments.getAsString(context, i));
            if (id == null) {
                return null;
            }
            if(id.equals(actualId)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size >= 2;
    }
}
