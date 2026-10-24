package com.dawnduskterminal.mixin;

import com.dawnduskterminal.client.GeckoLibResetHints;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationProcessor;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 只重置真正需要重置的骨骼
 *
 * <p>GeckoLib 4.9.3 的 {@code tickAnimation} 在控制器处理完后会对
 * {@code getRegisteredBones()} 全部骨骼各做旋转 位置 缩放三段重置
 * 高塔恶魂的模型有 395 根骨骼 而大部分骨骼既不参与动画也不会偏离初始姿势
 * 每帧对它们做三段 lerp 和快照查表是纯浪费
 *
 * <p>这里把重置循环的骨骼集合缩小到
 * <ul>
 *   <li>动画快照还在 reset 过程中 或快照值不等于初始姿势的骨骼</li>
 *   <li>{@link GeckoLibResetHints} 标记的骨骼，例如高塔恶魂次级运动直接写入的那批</li>
 * </ul>
 *
 * <p>其余骨骼保持初始姿势 不进入循环
 * 重置集合按 {@link AnimatableManager} 分开维护 因为同一个 {@link AnimationProcessor}
 * 会被同模型的所有实体共用 不能把所有实体的骨骼状态混在一个集合里
 */
@Mixin(AnimationProcessor.class)
public abstract class GeckoLibAnimationProcessorMixin {
    // 不在字段初始化器里建表 这个 mixin 的初始化器在部分加载组合下没有被合进
    // AnimationProcessor 的构造函数 运行到 setActiveModel 时字段仍是 null
    @Unique
    private Map<AnimatableManager<?>, ObjectOpenHashSet<GeoBone>> ddt$resetBones;
    @Unique
    private Set<AnimatableManager<?>> ddt$seededManagers;
    @Unique
    private AnimatableManager<?> ddt$manager;

    @Unique
    private Map<AnimatableManager<?>, ObjectOpenHashSet<GeoBone>> ddt$resetBonesMap() {
        if (this.ddt$resetBones == null) {
            this.ddt$resetBones = new WeakHashMap<>();
        }
        return this.ddt$resetBones;
    }

    @Unique
    private Set<AnimatableManager<?>> ddt$seededManagerSet() {
        if (this.ddt$seededManagers == null) {
            this.ddt$seededManagers = Collections.newSetFromMap(new WeakHashMap<>());
        }
        return this.ddt$seededManagers;
    }

    @Inject(method = "tickAnimation", at = @At("HEAD"))
    private void ddt$captureManager(GeoAnimatable animatable, GeoModel model,
                                    AnimatableManager manager, double animTime,
                                    AnimationState state, boolean crashWhenCantFindBone,
                                    CallbackInfo ci) {
        this.ddt$manager = manager;
    }

    @Redirect(
        method = "tickAnimation",
        at = @At(
            value = "INVOKE",
            target = "Lsoftware/bernie/geckolib/animation/AnimationProcessor;getRegisteredBones()Ljava/util/Collection;"
        )
    )
    private Collection<GeoBone> ddt$bonesToReset(AnimationProcessor<?> processor) {
        AnimatableManager<?> manager = this.ddt$manager;
        if (manager == null) {
            return processor.getRegisteredBones();
        }

        ObjectOpenHashSet<GeoBone> bones = this.ddt$resetBonesMap().computeIfAbsent(
            manager, key -> new ObjectOpenHashSet<>());
        if (this.ddt$seededManagerSet().add(manager)) {
            bones.addAll(processor.getRegisteredBones());
        }

        // 动画可能在上一次被剪掉之后重新启动 这一帧的 changed 标记是唯一可靠的“重新入队”信号
        // 只查三个 boolean，不做任何快照或 lerp 运算
        for (GeoBone bone : processor.getRegisteredBones()) {
            if (bone.hasRotationChanged() || bone.hasPositionChanged() || bone.hasScaleChanged()) {
                bones.add(bone);
            }
        }

        Map<String, BoneSnapshot> snapshots = manager.getBoneSnapshotCollection();
        Iterator<GeoBone> iterator = bones.iterator();
        while (iterator.hasNext()) {
            GeoBone bone = iterator.next();
            if (GeckoLibResetHints.isAlwaysReset(bone.getName())) {
                continue;
            }
            BoneSnapshot snapshot = snapshots.get(bone.getName());
            if (snapshot != null && ddt$fullyReset(bone, snapshot)) {
                iterator.remove();
            }
        }
        return bones;
    }

    @Inject(method = "setActiveModel", at = @At("HEAD"))
    private void ddt$clearResetSets(BakedGeoModel model, CallbackInfo ci) {
        if (this.ddt$resetBones != null) {
            this.ddt$resetBones.clear();
        }
        if (this.ddt$seededManagers != null) {
            this.ddt$seededManagers.clear();
        }
        this.ddt$manager = null;
    }

    @Unique
    private static boolean ddt$fullyReset(GeoBone bone, BoneSnapshot snapshot) {
        if (snapshot.isRotAnimInProgress() || snapshot.isPosAnimInProgress()
            || snapshot.isScaleAnimInProgress()) {
            return false;
        }
        BoneSnapshot initial = bone.getInitialSnapshot();
        return ddt$close(snapshot.getRotX(), initial.getRotX())
            && ddt$close(snapshot.getRotY(), initial.getRotY())
            && ddt$close(snapshot.getRotZ(), initial.getRotZ())
            && ddt$close(snapshot.getOffsetX(), initial.getOffsetX())
            && ddt$close(snapshot.getOffsetY(), initial.getOffsetY())
            && ddt$close(snapshot.getOffsetZ(), initial.getOffsetZ())
            && ddt$close(snapshot.getScaleX(), initial.getScaleX())
            && ddt$close(snapshot.getScaleY(), initial.getScaleY())
            && ddt$close(snapshot.getScaleZ(), initial.getScaleZ());
    }

    @Unique
    private static boolean ddt$close(float a, float b) {
        return Math.abs(a - b) <= 1.0e-5F;
    }
}
