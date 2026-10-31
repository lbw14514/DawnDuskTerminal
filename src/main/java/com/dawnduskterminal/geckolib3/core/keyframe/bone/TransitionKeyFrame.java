package com.dawnduskterminal.geckolib3.core.keyframe.bone;

import com.dawnduskterminal.molang.runtime.ExpressionEvaluator;
import org.joml.Vector3f;

public class TransitionKeyFrame extends BoneKeyFrame {
    private final Vector3v postPoint;

    public TransitionKeyFrame(double firstStartTick, Vector3v firstPoint, Vector3v postPoint) {
        super(0, firstStartTick, firstPoint);
        this.postPoint = postPoint;
    }

    @Override
    public Vector3f getLerpPoint(ExpressionEvaluator<?> evaluator, double percentCompleted) {
        if (!isEnd(percentCompleted)) {
            return beginPoint.eval(evaluator);
        } else {
            return postPoint.eval(evaluator);
        }
    }
}
