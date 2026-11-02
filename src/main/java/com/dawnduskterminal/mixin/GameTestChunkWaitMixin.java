package com.dawnduskterminal.mixin;

import com.dawnduskterminal.DawnDuskTerminal;
import net.minecraft.Util;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestRunner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让 game test 在等不到「区块进入实体刻」时继续往下跑，而不是把整轮测试挂死
 *
 * <p>原版 {@code GameTestInfo.tick} 只有在 {@code chunksLoaded} 为真、或结构包围盒覆盖的每个区块都
 * {@code isPositionEntityTicking} 时才放结构并跑测试。实测会出现区块已经强制加载（{@code hasChunk}
 * 为真）却永远不进入实体刻的情况：那时这个条件永远为假，测试既不开始也不超时（超时由 {@code tickCount}
 * 判定，而判超时的 {@code tickInternal} 只在这个条件为真之后才会被调用）。于是
 * {@code GameTestServer.tickServer} 里 {@code testTracker.isDone()} 永远为假，而它叠加的
 * {@code waitUntilNextTick} 又不睡觉，结果就是每秒上万 tick 地刷同一行进度格子，永远不退出。实测 10 次
 * 里约 3 次会撞上，刷出来的日志超过 100 MB
 *
 * <p>这里等满 {@link #DDT$GRACE_MILLIS} 就把 {@code chunksLoaded} 置真放行。健康的一轮里这个条件在
 * 开局几个 tick 内就为真（GameTestServer 每秒几万 tick，几个 tick 只有零点几毫秒），所以兜底几乎不会
 * 触发；万一触发会打一条 warn，日志里看得见。本模组的测试都是同步断言（t=0），不依赖实体刻
 */
@Mixin(GameTestInfo.class)
public abstract class GameTestChunkWaitMixin {
    /** 走满这么多毫秒还没等到实体刻就不再等，用墙钟而不是游戏刻，因为 GameTestServer 的刻飞快 */
    @Unique
    private static final long DDT$GRACE_MILLIS = 5000L;

    @Shadow
    private boolean chunksLoaded;

    @Unique
    private long ddt$firstTickMillis = -1L;

    @Inject(method = "tick", at = @At("HEAD"))
    private void ddt$giveUpWaitingForEntityTicking(GameTestRunner runner, CallbackInfo ci) {
        GameTestInfo self = (GameTestInfo) (Object) this;
        if (this.chunksLoaded || self.isDone()) {
            return;
        }
        long now = Util.getMillis();
        if (this.ddt$firstTickMillis < 0L) {
            this.ddt$firstTickMillis = now;
            return;
        }
        if (now - this.ddt$firstTickMillis < DDT$GRACE_MILLIS) {
            return;
        }
        this.chunksLoaded = true;
        DawnDuskTerminal.LOGGER.warn(
                "DawnDuskTerminal game test {} waited {} ms for its chunks to enter entity ticking, running it anyway",
                self.getTestName(),
                now - this.ddt$firstTickMillis);
    }
}
