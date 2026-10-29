# YSM → GeckoLib 动画转换

本目录用于把 **Yes Steve Model** 的 Bedrock 动画文件转换为 GeckoLib 能够编译的形式。之所以需要该转换，是因为 YSM（2.3.0+）实现了几乎全部 Bedrock Molang，<em>并额外实现了一套自有标准库</em>，而 GeckoLib 对这两者均未实现。若把 YSM 动画文件直接复制进 GeckoLib 模组，得到的结果并非「动画损坏」，而是模型始终不动；后者远比前者更难排查。

## 直接拷贝失败的原因

GeckoLib 的 `MathParser`（4.9.3）强制要求以下三项约束，而 YSM 文件对三者均有违反：

| 约束 | GeckoLib 4.9.3 | YSM 文件 |
| --- | --- | --- |
| 表达式字符集 | `^[\w\s_+-/*%^&|<>=!?:;.,(){}]+$` | `ysm.second_order('头发角速度', …)` —— 该表达式使用单引号 |
| 函数库 | 仅提供 `math.*` | 提供 `ysm.*` 与 `ctrl.*`，以及 GeckoLib 并不支持的 `query.*` |
| 跨帧状态 | 无，每条表达式均以无状态方式求值 | `first_order` / `second_order` / `bone_rot` 均为逐帧滤波器 |

GeckoLib <em>逐个</em>烘焙动画片段，一旦解析失败便记录一条日志并跳过该片段（`BakedAnimationsAdapter.deserialize`）。被跳过的片段在运行时完全无法察觉：骨骼只是保持上一个姿势。在 `高塔恶魂` 这一模型上，该行为导致 **50 个片段中的 9 个**被丢弃，其中包括 `walk`、`death` 以及两个 `parallel` 物理层。

## 转换器做了什么

`ysm_to_geckolib.py` 把每条表达式改写为 GeckoLib 的文法，并输出一份审计报告；每处改动均带有分类。

### 两个 head 并非同一对象

`ysm.head_yaw` 表示<strong>相对身体</strong>的头部转角，而 GeckoLib 的 `query.head_y_rotation` 取自 `getViewYRot()`，属于<strong>世界坐标</strong>。俯仰方向不存在这一问题，因为身体不会俯仰。

若直接替换，后果并非「略有偏差」，而是数值被静默放大并使整只模型发生滚转：

- 对于 `RightEyesBase.position[0]` 中的 `head_yaw/180`，其相对值最大约为 0.25 格，而绝对值可达到 2 格，因此眼睛被推出面部之外。
- 在 `run` 片段中，`Root.rotation[2] = 0.3*head_yaw` 的绝对值可达到 54 度，于是跑动时<em>整只模型</em>随朝向滚转；模型受到一次攻击并转入逃跑状态时，其表现为「倒下再立起来」。

`query.body_y_rotation` 对应 `Mth.lerp(partialTick, yBodyRotO, yBodyRot)`，`math.min_angle` 对应 `Mth.wrapDegrees`，因此表格中该行即为 YSM 语义的原样翻译。

### 一级 —— 精确（本模型 173 处）

对于维基中写明与某个 query 为<em>同一个值</em>的 YSM 名字，逐个改名，不引入语义漂移。

| YSM | GeckoLib | YSM 维基 |
| --- | --- | --- |
| `ysm.head_pitch` | `query.head_x_rotation` | 表示俯仰角；身体不俯仰，因此两者同值 |
| `ysm.head_yaw` | `math.min_angle(query.head_y_rotation-query.body_y_rotation)` | 见下文「两个 head 不是同一个东西」一节 |
| YSM `query.head_y_rotation` | `query.head_x_rotation` | 维基指出：`ysm.head_pitch` 与它相同，即表示<strong>俯仰</strong> |
| YSM `query.head_x_rotation` | `math.min_angle(query.head_y_rotation-query.body_y_rotation)` | 维基指出：`ysm.head_yaw` 与它相同，即表示<strong>偏航</strong>，且必须相对身体取值 |
| `ysm.is_sneak` | `query.is_sneaking` | 两者同值 |
| `ysm.is_sleep` | `query.is_sleeping` | 两者同值 |
| `ysm.is_passenger` | `query.is_riding` | 两者同值 |
| `ysm.has_helmet` | `query.has_head_gear` | 两者同值 |
| `q.*` | `query.*` | 文档中写明的别名 |

同时，后两行并非笔误：<strong>YSM 自身的 head query 命名与 GeckoLib 相反</strong>。维基写的是「`ysm.head_yaw` 与 `query.head_x_rotation` 相同」以及「`ysm.head_pitch` 与 `query.head_y_rotation` 相同」，而 GeckoLib 的 `query.head_x_rotation` 为 `getViewXRot`（俯仰），`query.head_y_rotation` 为 `getViewYRot`（世界偏航）。

转换器最初仅按同名方式搬运源动画中裸写的 `query.head_*rotation`，于是 `pre_parallel2` 中的颈部被改写为绕 X 轴旋转 `-0.5*世界偏航`（最大 ±90°）；而源文件实际要求的是 `[-0.5*俯仰, -0.5*相对偏航, 0]`，其表现为颈部上翻、越出领口，即「脖子穿模」所描述的现象。

这两项改名必须同时完成，因此转换器先写入占位符、再统一展开：偏航那一项的输出中同样含有 `query.head_y_rotation`。

### 二级 —— 有状态滤波与结构性修补（401 处）

**有状态滤波器**指 `ysm.first_order(name, input, response)` 与 `ysm.second_order(name, input, frequency, coefficient, response)`，它们是<strong>按 `name` 记忆状态的递归滤波器</strong>，每帧积分一次；而 `ysm.bone_rot(name)` 读取的是<strong>上一帧</strong>的骨骼变换。GeckoLib 不存在跨帧状态，因此任何表达式改写都无法复现这些滤波器。

转换器改为代入滤波器的<strong>静态可求值输入信号</strong>。

该替换的效果优于直观预期：用参考实现（归属见下文）实测可知，两个滤波器的<strong>稳态增益均为 1</strong>，且 `physics_reference.py` 已对本模型用到的每一组 `(frequency, coefficient, response)` 完成验证：

```
 freq  coef  resp      gain   90% at
  1.0   0.8   0.0    1.0000 11 ticks
  2.0   0.4   0.0    1.0000  4 ticks
  2.5   0.3   0.0    1.0000  3 ticks
  3.0   0.3   1.0    1.0000  2 ticks

response   gain   90% at      （first_order）
     0.3 1.0000 13 ticks
     0.5 1.0000 22 ticks
```

也就是说，滤波器最终收敛到的正是输入值本身。因此，把 `second_order(name, X, …)` 替换为 `X` 在<strong>稳态</strong>意义上是精确的，仅丢失暂态部分，即 2 到 11 个 tick（0.1 到 0.55 秒）的滞后以及过冲。真正丢失的是<em>次级运动</em>，即 `walk` / `jump` / `swim` / `fly` 中的布料、旗帜与头发链；这类运动把每根骨骼自身的上一帧旋转作为下一根骨骼的驱动输入，而该级联关系不存在无状态等价物。

**结构性修补** 涉及三条 GeckoLib 文法规则。这些规则只有在真实客户端中运行之后才会暴露，因为检查器的第一版仅建模了字符集，未建模解析器对嵌套表达式的处理：

| 规则 | 日志里的失败 | 修法 |
| --- | --- | --- |
| `VARIABLE_FORMAT = ^[a-z_]+\.[\w+_]+$` 只允许<strong>一个点</strong> | `Unable to parse compiled symbols from expression: [Left[v.roaming.expe]]` | 把 YSM 三级的 `v.roaming.*` 改写为 `v.roaming_*`（`v.roaming.expe` → `v.roaming_expe`） |
| 条件式必须在同一层内出现配对的 `:`，若 `MathParser.compileTernary` 找不到冒号便返回 null；即 GeckoLib 不接受 YSM 的<strong>无冒号条件式</strong> `cond?value` | `Failed to parse expression '5.63304+(query.ground_speed>0?math.sin(query.anim_time*720)*2)'` | 补写为 `cond?(value):0`：YSM 条件为假时求值结果为 `null`，在数值上下文中按 0 处理，因此补 0 是精确的 |
| 函数<strong>实参列表</strong>中的三元运算符无法编译（此为经验规则，并非 jar 中的常量） | 同上的那条日志曾被归因于本条规则 | 先把三元求值并写入前一条语句的 `t.hoistN`，调用处改为读取该变量 |

提升必须上移至<strong>顶层</strong>：把三元从嵌套调用中提取出来会产生一条赋值，而该赋值仍位于上一层调用的实参列表之内，含 `?` 的赋值与三元一样无法编译。

上表第二条才是 `ride` 丢失的真正原因。此前该失败被归因于第三条，于是转换器改为提升三元（提升本身是精确的），而 `ride` 仍然失败：`t.hoist0=(cond?X)` 中的 `?` 依然缺少冒号。日志中已有现成证据：含冒号的三元即使位于括号之内也能正常加载（`parallel0` 被提升出的 `t.hoist0=(…(0<0?(0<0?-180:180):0))` 即属此例），而缺冒号的 `ride` 不能加载。致命之处在于缺少冒号，而非括号本身。

另有一条属于<strong>语义</strong>问题，而非文法问题：Bedrock 的 `timeline` 在 GeckoLib 中只是自定义指令关键帧。`KeyFramesAdapter.buildCustomFrameData` 把 `timeline` 的字符串读取为 `CustomInstructionKeyframeData`，`AnimationController` 仅将其交给已注册的 handler，<strong>并不会对该字符串求值</strong>；而本模组未注册 handler，因此整个 `timeline` 在客户端中不产生作用。

该模型包恰好把默认值写在 `timeline` 之中，因此这些变量在 GeckoLib 一侧恒为 0：`pre_parallel2` 的 `v.roaming.shoe=1` 不生效，鞋子的 `scale` 因此为 0（靴子不可见）；`idle`、`walk`、`run` 等片段写入的 `v.sleevefix` 不生效，`parallel0` 中四条 `v.sleevefix*(…)` 所对应的袖口衬里通道因此恒为 0。

修改方法是将<strong>幂等的常量赋值</strong>移入片段自身的 `molang` 骨骼通道（`v.bv=…`、`v.roaming.size=…` 原本就写在该处，原作者即如此使用）。GeckoLib 每帧求值骨骼通道，因此常量赋值执行一次与每帧执行一次等价。非幂等的赋值（例如 `jump` 的 `v.jumpsteb=1-v.jumpsteb`）若移入该处会每帧翻转，只能保留在 `timeline` 中，而该处赋值仍然不会被求值。

`v.forward`、`v.hg`、`v.hv`、`v.bg`、`v.bv` 属于被文件读取、但<strong>文件从未赋值</strong>的变量：它们原本由外部自定义函数脚本提供取值，而该模型包并不包含这些脚本。YSM 文档写明未初始化的变量为 `null`，且 YSM 在数值上下文中将 `null` 视为 0（"参与数值类计算时被视为 0 或 false"），因此这些变量在 YSM 中同样读取为 0。转换器的输出与 YSM 自身行为一致，未凭空生成取值（`v.sleevefix` 与 `v.roaming.*` <em>不属于</em>此类：它们由片段自身的 `timeline` 赋值，见上文）。

### 并行层：YSM 是加法混合，GeckoLib 是覆盖

这是分层处理中最反直觉的一点。GeckoLib 的官方注释写明「多个控制器同时播放的动画会互相覆盖同一根骨骼」，其实现方式同样是逐个控制器执行 `bone.setRotX(动画值 + 初始姿势)`（`AnimationProcessor.tickAnimation`）；控制器按注册顺序遍历，后注册者覆盖先注册者（`ControllerRegistrar.build()` 返回 `Object2ObjectArrayMap`，其迭代顺序即插入顺序）。

因此，「一个 YSM 层对应一个 GeckoLib 控制器」的做法<strong>不能</strong>复现 YSM 的加法混合：并行层在同一通道上写入的取值会整段覆盖主动画的关键帧。待机状态下的袖口即为典型例子：

```
idle/RightSlave4      19.9614835381+3*math.sin(120*query.life_time-120)    ← 源文件的待机摆动
parallel0/RightSlave4 0.5*(math.clamp(-0.02*20*query.yaw_speed,-60,60))   ← 站着不动时是 0
```

`parallel0` 注册在后，其取值为 0，因而覆盖了待机摆动，外部表现即「衣物不动」与「弯折处没效果」。

修改位于 `TowerGhastEntity.registerControllers`：<strong>注册顺序即优先级</strong>，物理层（`parallel0`、`pre_parallel0`、`pre_parallel1`）最先注册，主动画居中，换形开关（`pre_parallel2`）最后注册。主动画因此在布条与袖口上优先于那些常量 0，而 `pre_parallel2` 的形态缩放能够覆盖 `death` 之类的关键帧（YSM 一侧的缩放为相乘语义，`v.roaming.type` 要把未选中的形态缩放到 0，必须以覆盖为前提才能成立）。

**并行层不能删除**：对于没有任何控制器写入的骨骼，GeckoLib 只执行一次速度很慢的回位插值（`getBoneResetTime`，默认 5 秒），因此 `TowerGhastBonePose` 的叠加写入会逐帧累积，实测症状为布条的每个关节都出现剧烈旋转。物理层中的常量 0 正是「每帧将骨骼重置回初始姿势」的来源，为叠加写入提供干净的基准。

尚存一处已知差距：并行层中「速度驱动」的旋转项会在主动画的同通道上被覆盖（即袖口衬里在 `run` 中的那几度），因为 GeckoLib 不提供加法混合。

### 三级 —— 丢弃（171 处）

此类构造没有等价物，因此该项被改写为 `0`，并记入报告：

| 构造 | 原因 |
| --- | --- |
| `ysm.bone_rot/pos/scale/pivot_abs('name')` | 表示上一帧的骨骼变换，需要状态支持 |
| `ysm.perlin_noise(seed,x,y,z)` | GeckoLib 未提供噪声函数 |
| `ysm.particle` / `ysm.abs_particle` / `play_sound` / `stop_sound` | 属于副作用，不产生取值 |
| `ysm.keyboard` / `ysm.mouse` / `time_delta` / `fps` | 客户端输入与计时未暴露给 GeckoLib |
| `q.position_delta`、`q.eye_target_y_rotation`、`q.position` | 属于 YSM 新增的 query，GeckoLib 未实现 |
| `q.yaw_speed` | GeckoLib <em>确有</em> `query.yaw_speed`，早先被误判为不存在；补入时还需乘以 20 以换算为每秒 |
| `??` 空值合并 | GeckoLib 没有 null，因此直接使用原本即默认取 0 的变量 |
| `#` 开头的关键帧指令 | 音效、粒子与指令关键帧 |

## 资源必须放置于 GeckoLib 的检索路径

文法正确仍然不够。GeckoLib 的 `DefaultedGeoModel` 把每条路径拼接为 `<folder>/<subtype>/<path>.<ext>`，而 `DefaultedEntityGeoModel.subtype()` 返回字面量 `"entity"`：

```java
// DefaultedGeoModel 的字符串常量
geo/<path>/<subtype>.geo.json
animations/<path>/<subtype>.animation.json
textures/<path>/<subtype>.png
// DefaultedEntityGeoModel.subtype() -> ldc "entity"
```

因此，符合约定的路径应为 `geo/entity/…`、`animations/entity/…`、`textures/entity/…`。该问题曾耗费实际排查时间：动画文件放在 `animations/tower_ghast.animation.json`，而模型查找的是 `animations/entity/`，客户端表现为实体能够渲染但始终不动。

因此 `TowerGhastRenderer` 直接写出这三条路径，不依赖上述约定；代码中的路径即为 jar 中的文件，不存在任何需要推断的环节。

## 直接拷贝的实际结果

在真实客户端中，把 YSM 文件原样复制进去会得到如下日志：

```
[GeckoLib/]: Unable to parse animation: ride / parallel0 / pre_parallel1 / pre_parallel2
[GeckoLib/]: Unable to find animation: idle for TowerGhastEntity      (x 42825)
```

第二行才是关键：片段缺失时 GeckoLib 不会报错退出，而是逐帧输出日志并保持姿势。其症状为实体站立不动，很容易被误判为模型或渲染器的问题。`validate_geckolib.py` 的作用就是让这项检查在构建期执行，而不依赖人工查看日志。

## 本模型上的实测结果

```
片段数              50
改动的表达式        745 条，分布在 27 个片段
按级别              173 / 401 / 171  （一级 / 二级 / 三级）
GeckoLib 校验       50 个片段全部通过 GeckoLib 的解析规则
```

下表逐片段列出动态值（由 query 或变量驱动的取值）的保留数量。除两个物理层之外，每个片段均<strong>全部</strong>保留，损失集中于这两层：

```
片段          动态值入   动态值出   丢失
parallel0          138         82     56
pre_parallel0       41         24     17
其余 48 个片段    1202       1202      0
-----------------------------------------
全部 50 个片段    1381       1308     73
```

具体而言，`parallel0` 的 93 根骨骼中有 <strong>87 根</strong>已不再包含非平凡内容，`pre_parallel0` 则是 32 根中的 21 根；这两个片段<em>正是</em>模型的次级运动。

在这 73 条丢失的通道中，<strong>64 条属于 `ysm.bone_rot` 的父带子递次滞后</strong>（`parallel0` 56 条、`pre_parallel0` 8 条）。这些通道并非在文件转换阶段丢失，而是在运行时由 `client/TowerGhastBonePose` 的链条表（64 行，与这里的通道一一对应）补回。其余 9 条为 `pre_parallel0` 中 `GhastLeg*` 的 `ysm.perlin_noise`（各自使用独立的 seed，并非发丝所用的 1492），而 `Ghast` 这棵子树被 `pre_parallel2` 的 `v.roaming.type==1?…:0` 缩放到 0，在默认外观下（形态切换=0）不可见，因此未接入。

## 文件

| 文件 | 用途 |
| --- | --- |
| `ysm_to_geckolib.py` | 转换器；`--report` 用于写出逐子句的审计结果 |
| `validate_geckolib.py` | <strong>需要运行的就是该脚本</strong>：按 GeckoLib 真实的解析规则检查文件，并列出会被静默跳过的片段。它同时是全仓库唯一的 GeckoLib 语法规则模块，另外两个脚本从这里导入 |
| `ysm_selftest.py` | 自测脚本，用于固定每一条转换规则的预期结果 |
| `ysm_analyze.py` | 清点文件所使用的 YSM 构造，以及会被 GeckoLib 跳过的片段 |
| `ysm_dump.py` | 导出指定片段的原始表达式 |
| `physics_reference.py` | 滤波器公式，用于量度二级替换的误差 |

```bash
python ysm_to_geckolib.py <in.animation.json> <out.animation.json> --report report.json
python validate_geckolib.py <out.animation.json>   # 预期：全部片段通过
python ysm_selftest.py
```

回归方法如下：取源模型包中的 `animations/main.animation.json` 重新转换一次，其输出应与 `assets/dawnduskterminal/animations/tower_ghast.animation.json` 逐字节相同：

```bash
python ysm_to_geckolib.py <源模型包>/animations/main.animation.json /tmp/regen.json
cmp /tmp/regen.json src/main/resources/assets/dawnduskterminal/animations/tower_ghast.animation.json
```

## 源模型包中可用的文件

`ref/高塔恶魂/` 是一套完整的 YSM 模型包，本模组仅使用其中三个文件：

| 源文件 | 去处 |
| --- | --- |
| `models/main.json` | 逐字节复制为 `assets/dawnduskterminal/geo/tower_ghast.geo.json` |
| `animations/main.animation.json` | 经 `ysm_to_geckolib.py` 转换后成为 `animations/tower_ghast.animation.json` |
| `textures/ghast.png` | 逐字节复制为 `textures/entity/tower_ghast.png` |

其余文件供「玩家形态」使用，与这只生物无关：

- `models/arm.json` + `animations/arm.animation.json`：第一人称手臂模型与 52 个 `hold_mainhand:*` 手持动画。
- `models/arrow.json` + `animations/arrow.animation.json` + `textures/arrow.png`：箭矢形态。
- `animations/` 下另外 8 个文件是为其他模组提供的兼容层，分别为：`tac`（TACZ 枪械）、`tlm`（车万女仆）、`parcool`（跑酷）、`swem`（骑马）、`carryon`（搬运）、`slashblade`（拔刀剑）与 `extra`（由玩家手动触发的 idle1-7 与 bad1-5）。
- `sounds/*.ogg`：其中 `bad1-5` 为受击语音，`id1-7` 为前述 `extra` 动画的配音（此处用作待机语音），`attack` 为攻击音效，而这只生物没有攻击行为，因此该项留空。前两组已接入 `ModSounds`，变体由 `sounds.json` 中同一事件的多个文件交由原版随机挑选。
- `controller/parallel_controllers.json`：YSM 的并行动画控制器。其分层已在 `TowerGhastEntity` 中用 GeckoLib 控制器重写，该文件是分层关系的原始依据。

`ysm.json` 为包元数据，`avatar/` 为作者头像，`sounds/desktop.ini` 为 Windows 系统产生的冗余文件。

## 归属

用于量度二级近似误差的 `first_order` / `second_order` 公式，取自
**[OpenYSM](https://github.com/OpenYSM/OpenYSM)**
（`client/animation/molang/functions/physics/`，MIT 许可）的参考实现，OpenYSM 是 Yes Steve Model
的开放重实现。其中的 `SecondOrder` 把该算法归功于
[*Giving Personality to Procedural Animations using Math*](https://www.youtube.com/watch?v=KPoeNZZ6H4s)。

Yes Steve Model 本身采用<strong>保留所有权利</strong>的授权；本仓库未从其获取任何内容。
本文档记录的行为来自公开维基与 OpenYSM。

## 恢复次级运动

次级运动无法烘焙，只能在运行时积分。本节说明其原因，以及目前的完成进度。

### 未采用烘焙的原因

本节原先的记载为「按 20 fps 遍历片段的关键帧，逐帧求值有状态表达式，把结果写成关键帧」。经数据核查之后，该方案不成立：

- 滤波器的输入是运行时的动态量，其实际调用点如下：

  ```
  ysm.second_order('头发垂直', math.clamp(-3*q.vertical_speed+v.hv,-10,120), 2, 0.6, 0)
  ysm.second_order('前2x',     ysm.bone_rot('FrontFragFlag'), ...)
  ```

  输入为 `ysm.head_yaw`、`q.vertical_speed`、`q.ground_speed`、`q.yaw_speed` 以及上一帧的骨骼旋转。
  离线烘焙必须将它们固定为常数，由此得到的头发运动将完全静止，效果比当前「直接使用滤波器的输入」更差。
- `parallel0` 与 `pre_parallel0` 这两层<strong>连 `animation_length` 都没有</strong>（`loop: true`，没有长度）。
  它们并非时间轴，而是逐帧函数，不存在可供烘焙的关键帧轴。

### 当前做法

状态保存在客户端，逐 tick 积分，结果在渲染层写回骨骼：

| 文件 | 职责 |
| --- | --- |
| `entity/TowerGhastPhysics` | 两支滤波器的公式，与 `physics_reference.py` 逐字对应 |
| `client/TowerGhastBoneFilters` | 每个实体一份的跨帧状态（12 支实体滤波器加 64 条链条滤波器）；输入取自实体，单位与 GeckoLib 的 `MolangQueries` 一致 |
| `client/TowerGhastBonePose` | 在 `setCustomAnimations` 中完成动画求值之后逐骨骼写回 |

`gametest/TowerGhastPhysicsTests` 固定了阶跃响应表的取值：表中记录 `1.0/0.8/0.0` 在 11 tick 达到 90%，Java 实现必须同样为 11 tick，因此两者并非「近似」，而是同一个滤波器。

单位与符号依照 GeckoLib 对表达式的处理方式确定：常数以外的旋转值先转换为弧度，再将 X 与 Y 取反，Z 不取反
（`AnimationController.getAnimationPointAtTick`）。旋转为「初始姿势加偏移」，位置与缩放为绝对值。

### 与 GeckoLib 内置 query 区别对待的四处

以下四点是在实机运行中出现两类症状（眼睛被推出面部之外、头发看不出运动）之后才查明的，均已写入
`client/TowerGhastBoneFilters` 的类注释：

| 项 | 源动画的语义 | GeckoLib 的 `query.*` | 处理 |
| --- | --- | --- | --- |
| 头部偏航 | 相对身体（Bedrock） | `getViewYRot()`，世界坐标 | 取 `wrapDegrees(yHeadRot - yBodyRot)` |
| `ground_speed` `vertical_speed` `yaw_speed` | 单位为每秒 | 单位为每 tick | 乘以 20 |
| `head_x_rotation` 取值时刻 | — | `getViewXRot(0)` 提供的是上一刻的取值 | 使用当前值 |
| `转体` 那条链的输入轴 | `query.head_x_rotation`，其语义为<strong>偏航</strong>（见「一级」一节） | `query.head_y_rotation` 才是世界偏航 | 取「头的世界偏航」，即累积的世界朝向加相对头部偏航 |

第一条影响最大：眼睛对应的算式为 `head_yaw/180`，相对转角最大约为 0.25 格，而世界偏航可达到 2 格，眼睛因此直接被推出面部之外。

最后一条是「待机时衣物不摆动」的两个主要原因之一：恶魂在站立状态下依靠 `LookAtPlayer` 持续改变头部<strong>偏航</strong>，俯仰几乎不变，因此以俯仰作为输入等于使整条转体链失去作用。另一个原因是并行层的覆盖，见「并行层」一节。

源表达式的第一项 `query.eye_target_y_rotation`（视角朝向）在 GeckoLib 中不存在，改用<strong>累积的世界朝向</strong>代替：每 tick 累加 `wrapDegrees(yRot - yRotO)`，得到连续的世界朝向（若直接读取 `yRot`，取值会在 ±180 处跳变 360 度，滤波器的滞后会把该跳变放大为布条的一次突跳）；在此基础上加上相对头部偏航，即为源表达式在原侧计算出的量。

### `ysm.perlin_noise` 的真实实现

此处并非「仿造一个噪声」：OpenYSM（MIT）中对应的语句为

```java
STBPerlin.stb_perlin_noise3_seed(x, y, z, 0, 0, 0, seed)
```

因此照此实现即可，波形与 YSM 一致。发丝的表达式为 `±10*perlin(1492, life_time) ± 头发角速度`，
38 根骨骼之间仅在轴与符号上存在差异，对应表位于 `client/TowerGhastBonePose`。

噪声必须按<strong>帧</strong>取值（`(tickCount + partialTick) / 20`），而不能按 tick 取值：
GeckoLib 每帧都对动画求值，若按 20 Hz 取值，在 60 fps 下将表现为逐帧跳跃
（YSM 一侧的 `query.life_time` 同样是连续的）。

### 链条的还原方式（`ysm.bone_rot` 的递次滞后）

链条由 64 条 `F(父骨骼旋转)` 通道构成，其表位于 `client/TowerGhastBonePose` 的 `LAGS` 中，
一行对应源文件中的一条通道（顺序必须为父骨骼在前）。

- 每只实体每 tick 推进一步：读取父骨骼的<strong>合成</strong>角度（即其自身偏移加上所有祖先的偏移，
  也就是 `ysm.bone_rot` 所指的「骨骼的旋转」），将其送入二阶滤波，结果按 `sign*F(x) + parentGain*x` 保存。
- **必须在基础姿势写入完成之后才读取**。布条根部的运动（`0.5*turnLag`、头部跟踪、发丝滤波器）
  都是运行时逐帧写入的，而转换后的动画文件中这些通道为 `0`；若先读后写，读取到的即为 `0`，
  整条链条因此保持静止，表现为布条作为一块刚性平面随根骨骼转动。
- 上一 tick 的链条量必须单独记录（`TowerGhastBoneFilters.chainPose`，按骨骼与轴记录）。
  GeckoLib 每帧的 `tickAnimation` 会把骨骼重置回「关键帧 + 初始姿势」，`addRotation` 累加的量无法跨帧保留，
  直接读取骨骼只能得到动画叠加的那一部分；缺少该记录时，第二级之后全部为 `0`，同样只得到一块刚性平面。
- 读取合成角度是形成「鞭子」效果的关键：纯滞后的那几级（`F(x)` 形状）在稳态下等于输入的合成角度，
  而合成角度中已计入上一级自身转出的量，因此逐级累加（布条与发帘均为该效果，近似水母游动）。
  若只读取局部角度，每一级都收敛到与父骨骼相同的角度，整条布条将被拉直，
  实测症状为「最下面那段不弯」。
- 欧拉角逐级相加并非严格的矩阵合成，但每根布条基本只在单一轴上弯曲，其误差远小于实际计算四元数再回拆的做法。
- 每个渲染帧按 `partialTick` 在两个 tick 之间插值写回，采用<strong>叠加</strong>而非替换的方式：
  旋转的加法混合已在文件中折算完成（见「并行层」一节），此处叠加的是转换阶段无法保存的状态。
- 链条是一棵严格的父→子树，不存在自引用：`F(x)-x` 这类形状会把自身拉回父骨骼的姿势，不会累积偏移
  （`gametest/TowerGhastPhysicsTests` 固定了三种形状的稳态值）；纯滞后的那几级则逐级承接上一级的量，
  深度越大弯曲越多。

### 模型中不存在的骨骼

该套动画使用了 5 个模型中不存在的名称：`Breast`、`FLongHair`、`MLongHair`、`ElytraLocator` 与 `LongHair`。
这些骨骼由 YSM 自行补充进模型，GeckoLib 没有对应步骤，因此这些通道在移植之后必然为空。

`TowerGhastBonePose` 在首次渲染时会针对这些名称输出一次警告；链条表中的 33 根骨骼均存在于 geo 中，
因此该警告不会涉及它们。

### 已完成的转换范围

- 已完成：输入不含 `ysm.bone_rot` 的那一批，包括头发（`MLongHair`、`FLongHair`、`LeftHeadFrag`、`RightHeadFrag`、
  `ElytraLocator`）、胸口（`Breast`、`Carminite`、`LeftGuard`、`Bung`）、眼睛（`RightEyesBase`、`LeftEyesBase`）与
  布条转身滞后（`FragFlag` 一系），共 19 根骨骼、12 支滤波器。
- 已完成：发丝的 38 根通道（`perlin` 加 `头发角速度`），以及上述四处量纲与语义修正。
- 已完成：`ysm.bone_rot` 的递次滞后，共 64 条通道、33 根骨骼，其表位于 `client/TowerGhastBonePose`。
- 已完成：主状态机的死亡与受击两档。YSM 的宿主状态机在 `isDeadOrDying()` 为真时播放 `death`（Priority.HIGHEST），
  在 `hurtTime > 0` 时播放 `attacked`（Priority 2），其条件与优先级均照搬进 `TowerGhastEntity.mainAnimation`。
  同时将渲染器的 `getDeathMaxRotation` 覆写为 0，否则 GeckoLib 的通用倒地动画会与模型自带的死亡演出重叠。
- 未实现：仅存在于 YSM 补充模型中的那 5 根骨骼（除非自行补充几何体，否则无法实现）；
  `GhastLeg*` 的 perlin（该子树默认 scale=0，不可见）。

源模型中 `v.bv`、`v.bg`、`v.hv`、`v.hg` 由外部脚本提供取值，而该脚本不在这套模型包内；
YSM 对未初始化变量取 0，因此这几项同样按 0 处理；由此，`RightSlave*`、`LeftSlave*` 一系的整条通道恒为 0
（`v.sleevefix` 属于例外：它由各片段自身的 `timeline` 赋值，已移入 `molang` 通道，因此袖口衬里会产生运动）。

资料：[YSM molang 参考](https://ysm.cfpa.team/wiki/molang/ref/)、
[YSM molang 简介](https://ysm.cfpa.team/wiki/molang/index/)、
[YSM 变量](https://ysm.cfpa.team/wiki/molang/var/)、
[YSM 并行动画](https://ysm.cfpa.team/wiki/animation/parallel/)、
[YSM 主动画](https://ysm.cfpa.team/wiki/animation/main/)。
