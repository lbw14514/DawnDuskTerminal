# Bedrock / YSM 动画运行时

本模组的实体用 Yes Steve Model（YSM）的 Bedrock 模型与动画，**运行时解析，不做离线转换**。
这一份说明覆盖：运行时从哪来、模型包怎么放、新增一个模型要做什么、以及目前已知的差距。

## 运行时从哪来

| 目录 | 内容 | 来源 |
|---|---|---|
| `geckolib3/` | 几何解析、关键帧、控制器、渲染 | [TouhouLittleMaid](https://github.com/TartaricAcid/TouhouLittleMaid)（MIT） |
| `molang/` | Molang 的词法、语法与求值 | 同上 |
| `client/animation/` | 本模组自己的接线：模型包读取、`ysm.*` / `fn.*` 绑定、渲染器基类 | 本仓库 |

选这一份而不是 GeckoLib 的原因，是它把 YSM 需要的三件东西都做进去了：

- **加法混合**：`AnimationProcessor.tickAnimation` 对控制器名以 `parallel_` 开头的层做累加，其余层覆盖。
  YSM 的并行动画正是靠这个语义，GeckoLib 只有覆盖。
- **有状态函数**：`ysm.first_order` / `second_order`（按名字记忆的递归滤波器）、`ysm.bone_rot`（上一帧的骨骼变换）
  ——由 `PhysicsManager` 按渲染帧积分。
- **`timeline` 语句程序**：关键帧里那段 `v.xxx = ...;` 序列会被逐帧执行，而不是当成字符串交给空 handler。

另外它自带 `ysm.bone_pivot_abs`、`ysm.head_yaw/head_pitch`、`query.head_x_rotation`（YSM 语义：偏航）
等 YSM 名字，省掉一整层改名，也省掉「改名改错、模型静默不动」这类问题。

### 与上游 TouhouLittleMaid 的差异

| 差异 | 原因 |
|---|---|
| 删掉女仆专有的绑定（`tlm.*`、`maidEntityVar`、饥饿值、梯子朝向、键鼠输入） | 与本模组的实体无关 |
| `ysm.perlin_noise` 允许 2~4 个参数 | YSM 允许省略坐标（这套包的发丝写的是 `perlin_noise(1492, query.life_time)`），只接受 4 个会让整条表达式解析失败 |
| 补 `ysm.bone_pivot_abs` 的真实实现 | 上游与官方 YSM 3.0-dev 这里都是占位实现（恒返回 0）。这套包的布条物理每帧读它当「质点的当前位置」，返回 0 会让辅助骨骼的基矩阵变成反射 |
| 补 `fn.*` 五个四元数函数 | 模型包没有 `functions/` 目录，定义无从取得，按标准语义用 JOML 重建 |
| `query.eye_target_y_rotation` 返回**连续**朝向（逐 tick 累加 `wrapDegrees(yRot - yRotO)`） | 直接读 `getViewYRot` 会在跨 ±180 时跳 360 度，有状态弹簧把这一跳放大成一次猛烈抽动 |
| 主动画的选择不用 `AnimationEvent.isMoving()` | 这个 fork 里那个标志是 `limbSwingAmount <= 阈值`，含义是「几乎没在动」，直接用会把 walk 与 idle 播反。改用 `walkAnimation.speed()` 与速度判断，见 `client/TowerGhastRenderer` |
| 名称栏改为直接调 `LivingEntityRenderer.renderNameTag` | 上游用 mixin 开的口子，这里没必要 |
| 去掉 sodium / embeddium 的兼容分支 | 本模组不针对它们做兼容 |

## 模型包怎么放

一个模型包就是一个 YSM 模型包，**不改写任何文件**，放在 `assets/dawnduskterminal/ysm/<包名>/`：

```
ysm.json                 包元数据：properties.height_scale / width_scale / default_texture
models/main.json         Bedrock 几何
animations/main.animation.json   Molang 表达式写法的动画
textures/<名字>.png      贴图
controller/*.json        并行动画控制器（分层关系的原始依据）
```

现有两个包：`ysm/tower_ghast`（高塔恶魂）与 `ysm/ghast_elite`（大恶魂）。后者原本只有散装三个文件，
补齐了一份最小 `ysm.json`。

加载点是 `client/animation/YsmPacks`：`ResourceManager` → `Converter` / `RawGeometryTree` / `GeoBuilder`
得到几何，`JsonAnimationUtils` 得到动画，两者都按文件缓存在 `GeckoLibCache` 里。F3+T 或换资源包时由
`client/animation/YsmEntityReload` 整块作废。

## 新增一个模型要做什么

1. **放包**：按上面的布局复制进 `assets/dawnduskterminal/ysm/<包名>/`。
2. **动画实例**：写一个 `YsmAnimatable` 子类，构造时给出几何、动画、贴图三条路径，并实现 `mainClip()`
   ——返回主动画此刻该播的片段名，返回 `null` 表示这一只没有主动画（全部由并行层写）。
   `pre_parallel0..7` 与 `parallel0..7` 的循环控制器由基类按「片段存在才注册」自动接上。
3. **渲染器**：`YsmEntityRenderer` 子类，按需覆写 `scaleOf`（缩放）、`getRenderType`、`suppressDeathRotation`，
   然后在 `EntityRenderersEvent.RegisterRenderers` 里注册。
4. **绑定**：模型用到 `ysm.*` / `fn.*` 里没有的名字时补在 `YsmBinding` / `FnBinding`。
   **缺绑定不会报错**，只会让那一条表达式整条失效（表现为某个通道不动），所以第 5 步别省。
5. **校验**：见下一节。

## 校验

新包的动画与几何能不能被解析，可以在不启动客户端的情况下查出来：

```
./gradlew compileJava
java -cp "<build/moddev/clientLegacyClasspath.txt 的内容>:build/classes/java/main:build/moddev/artifacts/neoforge-<版本>-merged.jar:." Check \
    assets/dawnduskterminal/ysm/<包名>/models/main.json \
    assets/dawnduskterminal/ysm/<包名>/animations/main.animation.json
```

`Check` 是个一次性的小程序（见下），它会打印几何的骨骼与立方体数量，以及每条片段解析成功还是失败。
现在两个包的结果是：高塔恶魂 50 条、大恶魂 2 条，全部通过。

```java
// Check.java —— 只依赖运行时本体，不碰客户端
RawGeoModel raw = Converter.fromInputStream(new FileInputStream(geoFile));
GeoModel model = GeoBuilder.getGeoBuilder().constructGeoModel(RawGeometryTree.parseHierarchy(raw));

Map<String, ObjectBinding> extra = Map.of("ysm", YsmBinding.INSTANCE, "fn", FnBinding.INSTANCE);
MolangParser parser = new MolangParser(new HashMap<>(extra));
for (Map.Entry<String, JsonElement> entry : JsonAnimationUtils.getAnimations(json)) {
    JsonAnimationUtils.deserializeJsonToAnimation(JsonAnimationUtils.getAnimation(json, entry.getKey()), parser);
}
```

表达式**解析**成功不等于**求值**正确：`ysm.bone_pivot_abs` 的符号、滤波器的稳态、四元数的手性这类问题
只能在客户端里看。修改这些之后必须实机确认一次。

## 布条与次级运动

布条、发丝、触手这类次级运动全部由 `ysm.first_order` / `ysm.second_order` 写进模型包的
`parallel0`（物理层）。它们按**渲染帧**积分，步长是帧间隔，与 YSM 的 `PhysicsManager` 一致；
弹簧频率写在表达式里（高塔恶魂的布条是 2 至 2.5 Hz），`render.ribbon_speed` 只缩放步长。

`ysm.bone_rot` 取骨骼自己的旋转（与 YSM 一致），**不要**改成累积到根的旋转：布条每级写的是
`second_order(父) - 父` 的滞后量，累积输入会把整条身体链的旋转也算进去，四级下来布条会卷成一团。

## 已知差距

| 差距 | 影响 | 恢复方式 |
|---|---|---|
| 贴图动画条（`.mcmeta`）不逐帧上传 | `entity/StringEntity` 的流体贴图只有第一帧 | 需要一个读 `.mcmeta` 并逐帧上传的实现 |
| `ysm.play_sound` / `stop_sound` / `particle` / `abs_particle` 是空实现 | 模型包自带的音效与粒子不播放 | 接 `SoundKeyframeEvent` / `IParticleListener` 再转发 |
| `v.roaming.*` 由宿主维护的变量恒为默认值 | `ysm.json` 的 `extra_animation` 一类开关不起作用 | 需要模型包选择界面，目前没有 |
| 模型包里 YSM 自己的补充骨骼（`Breast`、`LongHair` 之类）不存在 | 用到它们的通道为空 | 只有补几何才能实现 |

## 许可

TouhouLittleMaid 的 `geckolib3/` 与 `molang/` 为 MIT，见 `META-INF/LICENSE-TouhouLittleMaid.txt`。
几何与动画的数据来自模型包作者（CC BY-NC-SA 4.0），随模组分发时保留 `ysm.json` 里的作者信息。
