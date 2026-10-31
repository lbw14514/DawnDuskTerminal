package com.dawnduskterminal.geckolib3.core.keyframe;

import com.dawnduskterminal.geckolib3.core.controller.AnimationControllerContext;
import com.dawnduskterminal.geckolib3.core.keyframe.bone.BoneKeyFrame;
import com.dawnduskterminal.geckolib3.core.molang.context.AnimationContext;
import com.dawnduskterminal.molang.runtime.ExpressionEvaluator;
import org.joml.Vector3f;

public class KeyFramePoint extends AnimationPoint {
    public final BoneKeyFrame keyframe;

    public KeyFramePoint(double currentTick, BoneKeyFrame keyframe, AnimationControllerContext context) {
        super(currentTick, keyframe.getTotalTick(), context);
        this.keyframe = keyframe;
    }

    @Override
    public Vector3f getLerpPoint(ExpressionEvaluator<AnimationContext<?>> evaluator) {
        setupControllerContext(evaluator);
        return keyframe.getLerpPoint(evaluator, getPercentCompleted());
    }
}
