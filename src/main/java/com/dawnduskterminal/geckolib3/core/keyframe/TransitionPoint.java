package com.dawnduskterminal.geckolib3.core.keyframe;

import com.dawnduskterminal.geckolib3.core.controller.AnimationControllerContext;
import com.dawnduskterminal.geckolib3.core.keyframe.bone.BoneKeyFrame;
import com.dawnduskterminal.geckolib3.core.molang.context.AnimationContext;
import com.dawnduskterminal.molang.runtime.ExpressionEvaluator;
import org.joml.Vector3f;

public class TransitionPoint extends AnimationPoint {
    private final Vector3f offsetPoint;
    private final BoneKeyFrame dstKeyframe;

    public TransitionPoint(double currentTick, double totalTick, Vector3f offsetPoint, BoneKeyFrame dstKeyframe, AnimationControllerContext context) {
        super(currentTick, totalTick, context);
        this.offsetPoint = offsetPoint;
        this.dstKeyframe = dstKeyframe;
    }

    @Override
    public Vector3f getLerpPoint(ExpressionEvaluator<AnimationContext<?>> evaluator) {
        setupControllerContext(evaluator);
        return dstKeyframe.getTransitionPoint(evaluator, offsetPoint, getPercentCompleted());
    }
}
