package com.dawnduskterminal.client.animation;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.geckolib3.core.AnimatableEntity;
import com.dawnduskterminal.geckolib3.core.PlayState;
import com.dawnduskterminal.geckolib3.core.builder.AnimationBuilder;
import com.dawnduskterminal.geckolib3.core.builder.ILoopType;
import com.dawnduskterminal.geckolib3.core.controller.AnimationController;
import com.dawnduskterminal.geckolib3.core.event.predicate.AnimationEvent;
import com.dawnduskterminal.geckolib3.geo.IGeoEntity;
import com.dawnduskterminal.geckolib3.geo.animated.ILocationModel;
import com.dawnduskterminal.geckolib3.file.AnimationFile;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;

/**
 * 一只实体的动画实例：几何、贴图、动画文件的三条路径，加上 YSM 的分层控制器
 *
 * <p>控制器按 YSM 的注册顺序摆放，顺序本身就是语义：
 *
 * <ol>
 *   <li>{@code pre_parallel0..7} 最先注册，作为基础层，主动画会覆盖它们写下的同一通道
 *   <li>{@code main} 居中，按实体状态挑片段
 *   <li>{@code parallel0..7} 最后注册，且控制器名以 {@code parallel_} 开头，因此它们的值是<strong>叠加</strong>在
 *       主动画之上的（{@code AnimationProcessor.tickAnimation} 对这类控制器做累加而不是覆盖）
 * </ol>
 *
 * <p>不存在于动画文件里的层不注册：注册了也只会每帧打一条「找不到片段」的日志。
 */
public abstract class YsmAnimatable<M extends Mob> extends AnimatableEntity<M> implements IGeoEntity {
    private final ResourceLocation model;
    @Nullable
    private final ResourceLocation animationFile;
    @Nullable
    private final ResourceLocation texture;

    protected YsmAnimatable(M entity, @Nullable ResourceLocation model,
                            @Nullable ResourceLocation animationFile, @Nullable ResourceLocation texture) {
        super(entity, 60);
        this.model = model;
        this.animationFile = animationFile;
        this.texture = texture;
        if (model == null) {
            // 模型包路径写错时最典型的症状就是「只有阴影、看不到模型」：几何取不到，渲染层直接跳过
            DawnDuskTerminal.LOGGER.error("{} 没有可用的模型文件，实体只会留下阴影",
                entity.getType().toShortString());
        }
        this.registerControllers();
    }

    /** 主动画此刻应播放的片段名；返回 null 表示不播 */
    @Nullable
    protected abstract String mainClip(AnimationEvent<YsmAnimatable<M>> event);

    private void registerControllers() {
        for (int index = 0; index < 8; index++) {
            this.loopIfPresent("pre_parallel_" + index, "pre_parallel" + index);
        }
        this.addAnimationController(new AnimationController<>(this, "main", 2, this::mainState));
        for (int index = 0; index < 8; index++) {
            this.loopIfPresent("parallel_" + index, "parallel" + index);
        }
    }

    private PlayState mainState(AnimationEvent<YsmAnimatable<M>> event) {
        String clip = this.mainClip(event);
        if (clip == null) {
            return PlayState.STOP;
        }
        event.getController().setAnimation(new AnimationBuilder().addAnimation(clip, ILoopType.EDefaultLoopTypes.LOOP));
        return PlayState.CONTINUE;
    }

    /** 片段存在才注册；层在第一帧之前就固定下来，避免逐帧判断 */
    private void loopIfPresent(String controllerName, String clip) {
        if (!this.hasClip(clip)) {
            return;
        }
        this.addAnimationController(new AnimationController<>(this, controllerName, 0, event -> {
            event.getController().setAnimation(
                new AnimationBuilder().addAnimation(clip, ILoopType.EDefaultLoopTypes.LOOP));
            return PlayState.CONTINUE;
        }));
    }

    protected final boolean hasClip(String clip) {
        if (this.animationFile == null) {
            return false;
        }
        AnimationFile file = YsmPacks.animations(this.animationFile);
        return file.getAnimation(clip) != null;
    }

    /**
     * 先把几何与动画读进缓存，再交给基类切换模型
     *
     * <p>{@link AnimatableEntity#updateModel} 只查缓存，不负责解析文件；模型包是运行期读的资源，
     * 这里是唯一的装载点，缺少这一步的后果是「实体渲染出来但永远是静止的初始姿势」。
     */
    @Override
    public boolean updateModel() {
        if (this.model != null) {
            YsmPacks.geometry(this.model);
        }
        if (this.animationFile != null) {
            YsmPacks.animations(this.animationFile);
        }
        return super.updateModel();
    }

    @Override
    public ResourceLocation getModelLocation() {
        return this.model;
    }

    @Override
    public ResourceLocation getTextureLocation() {
        return this.texture;
    }

    @Override
    public ResourceLocation getAnimationFileLocation() {
        return this.animationFile;
    }

    @Override
    public ILocationModel getGeoModel() {
        return this.getCurrentModel();
    }

    @Override
    public void setYsmModel(String modelId, String texture) {
        // 本模组的实体各自绑定一个模型包，没有换皮需求
    }

    @Override
    public void updateRoamingVars(Object2FloatOpenHashMap<String> roamingVars) {
        // 没有宿主侧维护的 v.roaming.* 变量；模型包自带的取值由包内 timeline 负责
    }
}
