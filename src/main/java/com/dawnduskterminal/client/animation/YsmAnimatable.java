package com.dawnduskterminal.client.animation;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.geckolib3.core.AnimatableEntity;
import com.dawnduskterminal.geckolib3.core.PlayState;
import com.dawnduskterminal.geckolib3.core.builder.Animation;
import com.dawnduskterminal.geckolib3.core.builder.AnimationBuilder;
import com.dawnduskterminal.geckolib3.core.builder.ILoopType;
import com.dawnduskterminal.geckolib3.core.controller.AnimationController;
import com.dawnduskterminal.geckolib3.core.event.predicate.AnimationEvent;
import com.dawnduskterminal.entity.YsmPackUser;
import com.dawnduskterminal.geckolib3.core.molang.context.AnimationContext;
import com.dawnduskterminal.geckolib3.core.molang.util.StringPool;
import com.dawnduskterminal.geckolib3.core.molang.storage.VariableStorage;
import com.dawnduskterminal.geckolib3.geo.IGeoEntity;
import com.dawnduskterminal.molang.runtime.HashMapStruct;
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
    @Nullable
    private final YsmPack pack;

    /** 已经处理过的外观开关序号 */
    private int seenRoaming;
    /** 已经处理过的动作序号 */
    private int seenAction;

    /** 正在播放的手动动作片段，{@code null} 表示没有 */
    @Nullable
    private String pendingClip;
    /** 该片段结束的时刻，单位 tick（{@link #getSeekTime()} 的刻度） */
    private double pendingUntil;
    /** 没有长度的片段（例如 {@code statue} 这类姿态）按循环处理，直到下一个动作打断 */
    private boolean pendingLoop;

    protected YsmAnimatable(M entity, @Nullable ResourceLocation model,
                            @Nullable ResourceLocation animationFile, @Nullable ResourceLocation texture) {
        this(entity, model, animationFile, texture, null);
    }

    protected YsmAnimatable(M entity, YsmPack pack) {
        this(entity, pack.model(), pack.animation(), pack.texture(), pack);
    }

    private YsmAnimatable(M entity, @Nullable ResourceLocation model,
                          @Nullable ResourceLocation animationFile, @Nullable ResourceLocation texture,
                          @Nullable YsmPack pack) {
        super(entity, 60);
        this.model = model;
        this.animationFile = animationFile;
        this.texture = texture;
        this.pack = pack;
        if (model == null) {
            // 模型包路径写错时最典型的症状就是「只有阴影、看不到模型」：几何取不到，渲染层直接跳过
            DawnDuskTerminal.LOGGER.error("{} 没有可用的模型文件，实体只会留下阴影",
                entity.getType().toShortString());
        }
        this.registerControllers();
    }

    /**
     * 每帧入口：先把实体同步过来的外观开关与手动动作消费掉，再走动画流程
     *
     * <p>放在这里而不是各个渲染器里：所有模型包实体都靠同一个同步通道（值 + 递增序号），
     * 每帧检查序号是否变化，变了才应用，因此不需要逐帧写变量。
     */
    @Override
    public boolean setCustomAnimations(AnimationContext<?> context, AnimationEvent<?> event) {
        this.syncEntityState();
        return super.setCustomAnimations(context, event);
    }

    private void syncEntityState() {
        if (!(this.entity instanceof YsmPackUser user)) {
            return;
        }
        int roamingSeq = user.roamingSeq();
        if (roamingSeq != this.seenRoaming) {
            this.seenRoaming = roamingSeq;
            YsmPackUser.parseRoaming(user.roamingBlob()).forEach(this::applyRoamingKey);
        }
        int actionSeq = user.actionSeq();
        if (actionSeq != this.seenAction) {
            this.seenAction = actionSeq;
            String key = user.actionKey();
            if (!key.isEmpty()) {
                this.playOnce(key);
            }
        }
    }

    /** 命令里用的是短名（{@code hat}），模型包里声明的是完整变量名（{@code roaming.hat}） */
    private void applyRoamingKey(String key, double value) {
        YsmPack.RoamingOption option = this.pack == null ? null : this.pack.roamingOption(key);
        if (option == null) {
            DawnDuskTerminal.LOGGER.warn("模型包 {} 没有声明外观开关 {}", this.packId(), key);
            return;
        }
        this.setRoaming(option.variable(), value);
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
        // 手动动作最后注册：控制器名不是 parallel_，因此它覆盖主动画与并行层，播完即退
        this.addAnimationController(new AnimationController<>(this, "extra", 2, this::extraState));
    }

    /**
     * 播一次模型包声明的手动动作，例如舞蹈、待机语音动作
     *
     * @param key {@code ysm.json} 的 {@code properties.extra_animation} 里的按钮键
     * @return 是否真的播了；键不存在、或片段不在合并后的动画文件里时为 false
     */
    public boolean playOnce(String key) {
        String clip = this.pack == null ? key : this.pack.extraClip(key);
        if (clip == null) {
            DawnDuskTerminal.LOGGER.warn("{} 的模型包没有手动动作 {}",
                this.entity.getType().toShortString(), key);
            return false;
        }
        Animation animation = this.getAnimation(clip);
        if (animation == null) {
            DawnDuskTerminal.LOGGER.warn("动画文件里找不到片段 {}", clip);
            return false;
        }
        this.pendingClip = clip;
        this.pendingLoop = animation.animationLength <= 0.0D;
        // 留一点余量：片段播完的那一帧还在插值到最后一帧，提前一 tick 收掉会看到回弹
        this.pendingUntil = this.pendingLoop
            ? Double.MAX_VALUE
            : this.getSeekTime() + animation.animationLength + 1.0D;
        return true;
    }

    /**
     * 写一个外观开关的取值，例如 {@code hat=1}、{@code cloth=2}
     *
     * <p>模型包的 {@code pre_parallel2} 读 {@code v.roaming.<键>} 决定部件缩放，因此这里写的就是
     * Molang 的 {@code v}（scoped 存储）。{@code roaming.*} 走结构体，{@code cloth} / {@code read}
     * 这类是顶层变量，两种都支持。
     */
    public void setRoaming(String variable, double value) {
        VariableStorage storage = (VariableStorage) this.getAnimationProcessor().getPublicVariableStorage();
        if (variable.startsWith("roaming.")) {
            int roamingKey = StringPool.computeIfAbsent("roaming");
            HashMapStruct struct = storage.getScoped(roamingKey) instanceof HashMapStruct current
                ? current : new HashMapStruct();
            struct.putProperty(StringPool.computeIfAbsent(variable.substring("roaming.".length())), value);
            storage.setScoped(roamingKey, struct);
        } else {
            storage.setScoped(StringPool.computeIfAbsent(variable), value);
        }
    }

    /** 本实体绑定的模型包；非模型包的实体返回 null */
    @Nullable
    public YsmPack pack() {
        return this.pack;
    }

    /** 本实体绑定的模型包 id；非模型包的实体返回 null */
    @Nullable
    public ResourceLocation packId() {
        return this.pack == null ? null : this.pack.id();
    }

    /** 手动动作是否正在播放 */
    public boolean isPlayingAction() {
        return this.pendingClip != null;
    }

    private PlayState extraState(AnimationEvent<YsmAnimatable<M>> event) {
        if (this.pendingClip == null) {
            return PlayState.STOP;
        }
        if (this.getSeekTime() > this.pendingUntil) {
            this.pendingClip = null;
            return PlayState.STOP;
        }
        event.getController().setAnimation(new AnimationBuilder().addAnimation(this.pendingClip,
            this.pendingLoop ? ILoopType.EDefaultLoopTypes.LOOP : ILoopType.EDefaultLoopTypes.PLAY_ONCE));
        return PlayState.CONTINUE;
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
        if (this.pack != null) {
            // 资源重载会把包缓存清掉；重取一次才会重新合并动画文件，否则 main 里的片段会丢
            YsmPacks.get(this.pack.id());
        }
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
