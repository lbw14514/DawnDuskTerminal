package com.dawnduskterminal.geckolib3.core.molang.context;

import com.dawnduskterminal.geckolib3.core.AnimatableEntity;
import com.dawnduskterminal.geckolib3.core.controller.AnimationControllerContext;
import com.dawnduskterminal.geckolib3.core.event.predicate.AnimationEvent;
import com.dawnduskterminal.geckolib3.core.molang.storage.IForeignVariableStorage;
import com.dawnduskterminal.geckolib3.core.molang.storage.IScopedVariableStorage;
import com.dawnduskterminal.geckolib3.core.molang.storage.ITempVariableStorage;
import com.dawnduskterminal.geckolib3.model.provider.data.EntityModelData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import java.util.Random;

public interface IContext<TEntity> {
    TEntity entity();

    AnimatableEntity<?> geoInstance();

    Minecraft mc();

    ClientLevel level();

    AnimationEvent<?> animationEvent();

    EntityModelData data();

    AnimationControllerContext animationControllerContext();

    Random random();

    <TChild> IContext<TChild> createChild(TChild child);

    ITempVariableStorage tempStorage();

    IScopedVariableStorage scopedStorage();

    IForeignVariableStorage foreignStorage();
}
