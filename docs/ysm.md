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
ysm.json                         包元数据：properties.height_scale / width_scale / default_texture
                                 / extra_animation / extra_animation_buttons
models/main.json                 Bedrock 几何（format_version 必须是 1.12.0）
animations/main.animation.json   主状态机动画
animations/extra.animation.json  手动动作（舞蹈、待机语音动作），可选
textures/<名字>.png              贴图，多张时用 properties.default_texture 指定默认那张
controller/*.json                并行动画控制器，仅作分层关系的参考，运行时不读
```

三条硬约束，违反任何一条的症状都不会好看：

| 约束 | 违反时的症状 |
|---|---|
| 主文件必须叫 `main`：`models/main.json`、`animations/main.animation.json` | 找不到主文件会退回「排序第一」，通常是别的东西，于是主状态机与并行层全落空 |
| 只有 `main` 与 `extra` 两个动画文件会被合并 | 放在别的名字里（`tlm` / `tac` / `arm`）的片段，控制器找不到 |
| 几何的 `format_version` 必须是 `1.12.0` | 加载时报 `几何文件版本 X 不被支持`，实体只剩影子 |

现有两个包：`ysm/tower_ghast`（高塔恶魂）与 `ysm/ghast_elite`（大恶魂）。后者原本只有散装三个文件，
补齐了一份最小 `ysm.json`。

加载点是 `client/animation/YsmPacks`：`ResourceManager` → `Converter` / `RawGeometryTree` / `GeoBuilder`
得到几何，`JsonAnimationUtils` 得到动画，两者都按文件缓存在 `GeckoLibCache` 里。F3+T 或换资源包时由
`client/animation/YsmEntityReload` 整块作废，并重新合并动画文件。

## 导入一个新模型

### 路线 A：不写代码，用资源包 + `/ddt pack`（推荐先试这个）

1. 把模型包复制成 `assets/<命名空间>/ysm/<包名>/` 的目录结构，塞进一个资源包（或直接改仓库）。
   命名空间随你，用 `mypack` 就放在 `assets/mypack/ysm/<包名>/`。
2. 进游戏，让实体换成这个包：

   ```
   /ddt pack @e[type=dawnduskterminal:tower_ghast,limit=1] mypack:my_model
   /ddt pack @e[type=dawnduskterminal:ghast_elite,limit=1] mypack:my_model
   ```

   建议总是写成 `命名空间:包名`；只写路径会按 `minecraft` 解析，等于去 `assets/minecraft/ysm/` 找。
   空串回默认包。
   F3+T 会重新扫描资源包，换文件不必重启游戏。

   所有实现 `entity/YsmPackUser` 的实体都接受这三条命令（目前是高塔恶魂与大恶魂）。
3. 看效果，按下一节的校验与症状表排查。

这条路能用到的东西是**模型包里自己声明的**那些：几何、贴图、主片段、`extra_animation` 的手动动作、
`extra_animation_buttons` 的外观开关。显示比例取自包里 `ysm.json` 的 `height_scale`。

不写代码的边界：主动画选哪个片段仍由实体的 Java 状态机决定（`TowerGhastEntity.mainAnimation`，
片名固定为 `idle` / `walk` / `run` / `attacked` / `death`）。模型包若不给这些片名，就只会停在一个姿势上。

### 路线 B：改代码，换掉默认包或接一个新生物

1. 放包（同上）。
2. 换默认包：改 `client/TowerGhastRenderer.PACK` 那一行常量；大恶魂在 `client/GhastEliteRenderer`。
3. 接一个新生物：写一个 `YsmAnimatable` 子类（构造时给出模型包，实现 `mainClip()` 返回当前该播的片段名，
   返回 `null` 表示没有主动画），再写一个 `YsmEntityRenderer` 子类（按需覆写 `scaleOf`、
   `getRenderType`、`suppressDeathRotation`），最后在 `EntityRenderersEvent.RegisterRenderers` 里注册。
   `pre_parallel0..7` 与 `parallel0..7` 的循环控制器由基类按「片段存在才注册」自动接上。
4. 补绑定：模型用到 `ysm.*` / `fn.*` 里没有的名字时补在 `client/animation/YsmBinding` / `FnBinding`。
   **缺绑定不会报错**，只会让那条表达式整条失效（表现为某个通道不动），所以第 5 步不能省。
5. 校验，见下一节。

### 模型包想用上现成的自定义能力，需要这样声明

| 能力 | 模型包里要有的东西 |
|---|---|
| 手动动作（`/ddt action`） | `animations/extra.animation.json` 里以动作名命名的片段，且 `ysm.json` 的 `extra_animation` 声明同名键 |
| 外观开关（`/ddt roaming`） | `ysm.json` 的 `extra_animation_buttons[].config_forms` 声明变量（`v.roaming.*` 或顶层 `v.*`），并在动画里用「缩放 = 变量」实现显隐 |
| 手持物品 | 几何里有 `RightHandLocator` / `LeftHandLocator` 骨骼 |
| 形态切换 | 像高塔恶魂那样用 `pre_parallel2` 里的 `v.roaming.type` 与顶层骨骼的 scale 做互斥显示 |

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

### 症状对照表

| 症状 | 最可能的原因 |
|---|---|
| 只有一圈阴影，看不到模型 | 包路径不对（日志里有 `没有 models/*.json（目录 assets/…）`），或几何版本不是 1.12.0 |
| 所有形态叠加显示、走与站都没动作 | 主文件没被认出来（没叫 `main.animation.json`），日志里连刷 `Could not load animation: idle` |
| 模型能显示但完全不动 | 动画文件的键名对不上（`main` / `extra`），或片段名与 `mainClip()` 期望的不一致 |
| 某一块（布条、发丝、触手）不动 | 那条表达式用了没绑定的 `ysm.*` / `fn.*` 名字，整条表达式被跳过 |
| `/ddt action` 没反应 | 键不在 `extra_animation` 里，或对应片段不在 `main` / `extra` 两个文件中（日志里有 `没有手动动作`） |
| `/ddt roaming` 没反应 | 包没声明那个变量（日志里有 `没有声明外观开关`），或动画里没有用它控制缩放 |
| 手里的物品飘在旁边 | 模型的手部 Locator 位置与约定不同，需要改 `YsmEntityRenderer.renderInHand` 的偏移 |

表达式**解析**成功不等于**求值**正确：`ysm.bone_pivot_abs` 的符号、滤波器的稳态、四元数的手性这类问题
只能在客户端里看。修改这些之后必须实机确认一次。

## 布条与次级运动

布条、发丝、触手这类次级运动全部由 `ysm.first_order` / `ysm.second_order` 写进模型包的
`parallel0`（物理层）。它们按**渲染帧**积分，步长是帧间隔，与 YSM 的 `PhysicsManager` 一致；
弹簧频率写在表达式里（高塔恶魂的布条是 2 至 2.5 Hz），`render.ribbon_speed` 只缩放步长。

`ysm.bone_rot` 取骨骼自己的旋转（与 YSM 一致），**不要**改成累积到根的旋转：布条每级写的是
`second_order(父) - 父` 的滞后量，累积输入会把整条身体链的旋转也算进去，四级下来布条会卷成一团。

## 手动动作（舞蹈、待机语音动作）

模型包把这些动作放在单独的 `animations/extra.animation.json` 里，由 `ysm.json` 的
`properties.extra_animation` 声明按钮键。`YsmPacks` 把 `main` 与 `extra` 合并成一份挂在主文件的位置上，
因此控制器按片段名就能找到它们。

触发方式：`/ddt action <目标> <键>`，键取模型包声明的按钮名（高塔恶魂是 `id1`–`id7`、`bad1`–`bad5`、
`statue`）。实体把「键 + 递增序号」同步到客户端，客户端看到序号变化就播一次，播完回主状态机。
`extra` 控制器最后注册、名字不是 `parallel_`，因此它会覆盖主动画与并行层。

## 外观开关（衣物、表情、体型、形态）

模型包用 `ysm.json` 的 `properties.extra_animation_buttons[].config_forms` 声明一批 Molang 变量，
再用 `pre_parallel2` 里「缩放 = 变量」的写法决定部件是否显示。高塔恶魂声明了这些：

| 命令里的键 | 变量 | 取值范围 | 作用 |
|---|---|---|---|
| `type` | `v.roaming.type` | 0–2 | 形态：0 娘化本体、1 恶魂、2 试验 115 |
| `size2` | `v.roaming.size2` | 0–3，步长 0.1 | 体型，0 等于 1 |
| `hat` `shoe` `hair` `tear` `shin` | `v.roaming.*` | 0/1 | 帽子、鞋子、发型、小珍珠、荧光 |
| `expe` | `v.roaming.expe` | 0–6 | 眼睛（7 种） |
| `expm` `pat` | `v.roaming.expm` `pat` | 0–6 / 0–7 | 嘴、胸口图案 |
| `cloth` `read` | `v.cloth` `v.read` | 0–2 / 0–1 | 换衣、脸红 |
| `mute` | `v.roaming.mute` | 0/1 | 静音（音效未接，暂无效） |

```
/ddt roaming @e[type=dawnduskterminal:tower_ghast,limit=1] hat 1
/ddt roaming @e[type=dawnduskterminal:tower_ghast,limit=1] type 1
/ddt roaming @e[type=dawnduskterminal:tower_ghast,limit=1] expe 3
```

不预置默认值：YSM 里未初始化的变量就是 0，因此默认是「不带配饰、形态 0、表情 0」。
变量写在 Molang 的 scoped 存储里（`YsmAnimatable.setRoaming`），`roaming.*` 走结构体，
`cloth` / `read` 是顶层变量。

## 模型包切换

模型包 id 同步在实体上，渲染器发现 id 变了就重建动画实例（模型、动画、物理状态都挂在实例上）。

```
/ddt pack @e[type=dawnduskterminal:tower_ghast,limit=1] dawnduskterminal:ghast_elite
/ddt pack @e[type=dawnduskterminal:tower_ghast,limit=1] ""
```

第二个命令把 `id` 用引号写成空串即回到默认包。目前两个包是给各自生物量身做的，互换只会证明机制可用，不会好看。

换包后的重建由 `YsmEntityRenderer.requestedPack` 负责：模型包 id 变了就丢弃旧的动画实例（模型、动画、
物理状态都挂在实例上），下一帧用新包重建。三份同步数据（包 id、外观开关、动作）定义在实体的
`SynchedEntityData` 上，`entity/YsmPackUser` 是它们的公共接口。

## 贴图动画条

原版只给图集里的贴图做帧动画，实体按 `ResourceLocation` 直接绑贴图时不会。而弦用的两张贴图是竖向的
32 帧条（`16x512` 与 `32x1024`），几何里声明的 `texture_height` 只有一帧高，UV 的 0..1 于是横跨整条竖条——
画出来就是把 32 帧压成一张。

`client/animation/AnimatedStripTexture` 为每条竖条注册一张**一帧大小**的动态贴图，按 `.mcmeta` 的
`frametime` 把当前帧拷进去再 `upload()`，渲染类型绑这张动态贴图。资源重载时整块作废重读。

## 手持物品

模型包的 `RightHandLocator` / `LeftHandLocator` 骨骼用来挂手上的物品，`YsmEntityRenderer.renderHeldItems`
在模型渲染完之后按这两条骨骼的矩阵画原版物品渲染。骨骼缩放到 0（模型包隐藏部件的做法）时不画。

高塔恶魂的包里还有 `BladeLocator`、`RifleLocator`、`PistolLocator`、`LeftWaistLocator` 等，
那是给拔刀剑、TACZ 之类模组用的，本模组暂不接。

测试：`/item replace entity @e[type=dawnduskterminal:tower_ghast,limit=1] weapon minecraft:iron_sword`。

## 已知差距

| 差距 | 影响 | 恢复方式 |
|---|---|---|
| `ysm.play_sound` / `stop_sound` / `particle` / `abs_particle` 是空实现 | 模型包自带的音效与粒子不播放 | 接 `SoundKeyframeEvent` / `IParticleListener` 再转发 |
| `v.roaming.*` 由宿主维护的变量恒为默认值 | `ysm.json` 的 `extra_animation` 一类开关不起作用 | 需要模型包选择界面，目前没有 |
| 模型包里 YSM 自己的补充骨骼（`Breast`、`LongHair` 之类）不存在 | 用到它们的通道为空 | 只有补几何才能实现 |

## 许可

TouhouLittleMaid 的 `geckolib3/` 与 `molang/` 为 MIT，见 `META-INF/LICENSE-TouhouLittleMaid.txt`。
几何与动画的数据来自模型包作者（CC BY-NC-SA 4.0），随模组分发时保留 `ysm.json` 里的作者信息。
