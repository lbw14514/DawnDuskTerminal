package com.dawnduskterminal.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

public class LineParamTrigger extends SimpleCriterionTrigger<LineParamTrigger.TriggerInstance> {
    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, double param) {
        trigger(player, instance -> instance.matches(param));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, double min, double max)
        implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
            Codec.DOUBLE.optionalFieldOf("min", -1.0D).forGetter(TriggerInstance::min),
            Codec.DOUBLE.optionalFieldOf("max", 1.0D).forGetter(TriggerInstance::max)
        ).apply(inst, TriggerInstance::new));

        public boolean matches(double param) {
            return param >= this.min && param <= this.max;
        }
    }
}
