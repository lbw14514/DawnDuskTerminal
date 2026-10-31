/*
 * Copyright (c) 2020.
 * Author: Bernie G. (Gecko)
 */

package com.dawnduskterminal.geckolib3.core.keyframe.event;


public class EventKeyFrame<T> {
    private final T eventData;
    private final double startTick;

    public EventKeyFrame(double startTick, T eventData) {
        this.startTick = startTick;
        this.eventData = eventData;
    }

    public T getEventData() {
        return eventData;
    }

    public double getStartTick() {
        return startTick;
    }
}