package com.dawnduskterminal.block;

import com.dawnduskterminal.block.entity.Experiment076BlockEntity;
import com.dawnduskterminal.world.Experiment076Reaction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 试验物品 076 摆下来的样子：那个每个面每一刻都在换贴图的异常方块
 *
 * <p>方块本身什么都不做，也没有状态：外观整个归客户端的 {@code client/Experiment076Renderer}，
 * 服务端只负责挂一个空的 {@link Experiment076BlockEntity} 上去让渲染器有个落点。所以
 * {@code getTicker} 不覆写——两边的 ticker 都是空的，换贴图是按世界时间算的，不需要每 tick 攒状态
 *
 * <p>方块模型那一层是普通的 cube_all（{@code minecraft:block/netherrack}），它负责三件事：
 * 遮挡邻居的面、给破坏粒子一张贴图、以及在方块实体渲染距离之外当兜底——方块实体只在 64 格内画，
 * 再远就只剩这一层看得见
 */
public class Experiment076Block extends Block implements EntityBlock {
    public Experiment076Block(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Experiment076BlockEntity(pos, state);
    }

    /**
     * 被<b>玩家挖掉</b>时起一场砷铅铁反应堆的反应，见 {@link Experiment076Reaction}
     *
     * <p>挂的是 {@code playerWillDestroy} 而不是 {@code onRemove}：只有玩家开始挖这一下才算，
     * 被爆炸炸掉、被活塞推走、被别的方块更新带走、被指令 clear 掉，一律<b>不</b>起反应。
     * 原版由 {@code ServerPlayerGameMode#destroyBlock} 在方块被移除之前调用它，所以只可能是服务端
     *
     * <p>反应的状态放在 {@link Experiment076Reaction} 里而不是方块实体上——方块一没，方块实体就跟着没了
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel server) {
            Experiment076Reaction.start(server, pos);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
