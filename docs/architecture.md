# 架构总览

一个 NeoForge 模组，新增「晨昏线」维度

维度里没有昼夜更替，太阳高度由玩家坐标在晨昏线法向上的投影决定

地形与群系来自原版主世界世界生成的移植版本，方块换成模组自己的调色板

## 模块职责

`advancement/` 自定义进度触发器与判定

`block/` 单独的自定义方块

`client/` 客户端渲染 天空 染色 屏幕效果 光影检测

`command/` `/ddt` 指令

`compat/` 特征排序兼容

`entity/` 自定义生物

`config/` 配置定义

`crafting/` 自定义 Ingredient

`fluid/` 自定义流体

`gametest/` 游戏内测试

`item/` 自定义物品

`mixin/` 针对原版的补丁

`network/` 网络包

`portal/` 传送门建造 触发 传送 状态

`progression/` Boss 进度追踪

`registry/` 全部 `DeferredRegister`

`server/` 服务端事件

`structure/` 结构白名单与摆放表

`world/` 群系源 晨昏线 密度包装 自定义 Feature 与方块 流体反应 状态

## 入口

主类 `com.dawnduskterminal.DawnDuskTerminal`

构造函数里按顺序注册各类 `DeferredRegister`

注册网络负载 `ModNetwork::registerPayloads`

在 `FMLCommonSetupEvent` 上注册流体反应 `FluidReactions::register`

挂服务端事件，见下方「事件」

客户端环境下调用 `DdtClient.init`

注册 COMMON 配置 `DdtConfig.SPEC`

静态方法 `id(path)` 生成 `dawnduskterminal:` 命名空间的 `ResourceLocation`

## 生物

`registry/ModEntities` 注册 `tower_ghast`（高塔恶魂）与 `string`（弦），属性走 `EntityAttributeCreationEvent`

两种生物的刷怪蛋在 `ModItems`（`tower_ghast_spawn_egg`、`string_spawn_egg`），用的是 NeoForge 的 `DeferredSpawnEggItem`：原版 `SpawnEggItem` 的构造器要一个现成的 `EntityType`，而物品注册跑在实体类型之前，这个子类收的是 `Supplier`，同时在 `FMLCommonSetupEvent` 里补发射器行为、在 `RegisterColorHandlersEvent` 里登记两层染色，客户端只剩一个 `minecraft:item/template_spawn_egg` 模型（贴图与原版刷怪蛋共用，颜色由染色层给）。`gametest/SpawnEggTests` 从 `SpawnEggItem#byId` 反查一遍，钉住这张表确实填上了

`entity/StringEntity`（弦）的默认形态（滑行型）只在不稳定流体的液面上活动：每 tick 把脚底钉回液面，位置一旦离开流体就弹回上一个合法点，所以它不会飞出去也不会沉下去。水平移动由 `StringMoveControl` 驱动，不经过地面寻路：行为层每 8 到 17 tick 给一个落在液面上的目标点，控制器**每 tick 都朝它推一次**，目标点进入 0.35 格才回到 `WAIT`。不能照抄原版 `MoveControl` —— 它一进 `MOVE_TO` 就把自己改回 `WAIT`，假设行为层每 tick 重给目标点（`PathNavigation` 正是这么做的），照抄的结果是「推一 tick 滑一程」，实测 20 tick 只走 0.2 格。空中每 tick 的推进量由 `getFlyingSpeed` 从移动速度属性折算成 `速度 × 0.045`：原版这一格对非玩家操控的生物返回恒定 0.02，速度属性完全不参与，大小变种与「追击比巡航快」那一档都会失效。折算后小型巡航约 9.2 格每秒、追击 12.4 格每秒（行走的玩家是 4.3，冲刺 5.6）。移速随体型走：大型的基准是小型的 1.35 倍（巡航 12.4、追击 16.7 格每秒），0.85~1.15 的随机体型偏移也一起乘，巡航与追击的倍率两档共用。它全身自发光：渲染层自己配了一个材质——自发光着色器（`RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER`）、不挂光图、保留半透明（流体贴图 alpha 230）、写掩码保持 `COLOR_DEPTH_WRITE`，贴图动画由 GeckoLib 的 `AnimatableTexture` 逐帧上传，与选哪个 RenderType 无关。**不能直接用原版 `RenderType.entityTranslucentEmissive`**：它的写掩码是 `COLOR_WRITE`，不写深度；1.21 的绘制顺序是「实体 → 半透明区块层（流体在这一层）→ 云」，后两者在实体之后按深度测试混合，实体不进深度缓冲就会被这两层整片盖住，看起来像被流体和云挡在后面。渲染层的实现在 `client/StringRenderer` 的 `glowRenderType`

「弦」的待机音要一直响，所以不用原版那条「每隔一段随机间隔掷一次」的路：`getAmbientSound` 返回 null，由 `tickAmbientSound` 按音效长度排期。`string_ambient` 的三个变体取自同一段素材、长度一致（413192 采样 / 44100 Hz = 9.37 秒 = 187 tick），当前这条还剩 8 tick 时起下一条，两条叠在一起听不出断点；音高固定 1.0，变调会改变实际时长、排期就对不上。`gametest/StringEntityTests` 手动跑「控制器写速度 → 原版 air 分支」两个动作，钉住每 tick 都推、到位即停、推进量跟速度属性走三件事

「弦」有三个行为变种（`StringEntity.Kind`，序号进同步数据 `DATA_KIND` 与存档 `Kind`），三种共用模型、贴图与属性，区别只在运动系统与索敌范围。运动系统由 `applyKind` 按变种整套装配，能借原版的直接借：

**滑行型**贴液面，`StringMoveControl` 只给水平推进（每 tick 推一次、到 0.35 格算到达），垂直位置由 `keepOnFluidSurface` 钉住；只打脚边一格有流体的玩家

**低空飞行型**整套是蜜蜂那套：`moveControl = new FlyingMoveControl(this, 20, true)`（原版类，蜂用的就是它）、`FlyingPathNavigation`、追人用原版 `MeleeAttackGoal`（寻路自己算三维路径，空中与岸上的玩家都追得到，索敌不看流体）。没有目标时 `FlyerWanderGoal` 照 `Bee.BeeWanderGoal` 的写法——`navigation.isDone() && nextInt(10)==0` 就挑一个液面上方两格的盘旋点、`navigation.moveTo(navigation.createPath(...), 1.0)` 飞过去；目标没了（被打死或跑掉）由 `ReturnToFluidGoal` 带回液体上空：远处寻路飞回去，近了照蜜蜂在巢口悬停那样每 tick 用 0.35 的速度给一次目标点。它的 `travel` 照 `FlyingMob#travel`（恶魂与幻翼的父类）**整段不扣重力**，`checkFallDamage` 照 `FlyingMob` 写成空实现——**光靠 `setNoGravity(true)` 飘着是不够的**：重力标记一旦被读档或别的代码改掉就会自由落体，而且俯冲追击时 `fallDistance` 会照常累计、落地结算摔伤；传送门流体不是 `#minecraft:water`，进水也不会像原版水那样清零 `fallDistance`

**游动型**是鱼那一套：`moveControl = FluidMoveControl` 照抄 `AbstractFish.FishMoveControl`——眼睛泡在流体里每 tick 补 0.005 浮力、速度按 0.125 的比重渐变、按目标点补垂直推力、只改朝向；`travel` 照抄 `AbstractFish#travel` 的水下分支（`moveRelative(0.01)` 推、`move` 走、整体乘 0.9、没目标时缓慢下沉）。原版判定里的 `isInWater()`（`#minecraft:water` 标签）换成本模组的传送门流体判定，这是唯一改动；`keepSubmerged` 再把脚夹在「池底往上 0.2」到「液面往下 0.9」之间兜住范围。只打泡在流体里的玩家

三种变种**只主动索敌玩家**（`NearestAttackableTargetGoal<Player>` + `isValidPrey` 里再判一次类型）。自然生成只出滑行型（`world/StringSpawner`，每 5 秒扫列，玩家周围 48 格内 5 只封顶）；低空飞行型与游动型只由滑行型分裂产生。滑行型每次受到**高于 2 点**的伤害有 **10%** 概率裂成一对——低空飞行型（抬高 1.5 格悬空）加游动型，自己 `discard` 掉（不是 `die`：不掉战利品、不算击杀、不播倒地动画），伴随末影人传送音与 60 颗传送粒子。两种新变种不会二次分裂

「弦」的刷怪蛋（`item/StringSpawnEggItem`）只肯放在传送门流体边上：`useOn`（点方块）、`use`（点流体源格）、`createDispenseBehavior`（发射器；默认行为在 `DeferredSpawnEggItem` 里是私有的，照着重写了一份）三条发放路径都先过 `StringEntity.canSpawnAt`——落点那一格是流体，或它正下方是流体，才准放，否则蛋不消耗

`entity/TowerGhastEntity` 由 GeckoLib 驱动，模型 动画 贴图从 Yes Steve Model 的 Bedrock 文件转换而来，转换与校验在 `tools/ysm/`

`client/TowerGhastRenderer` 把三条资源路径直接写出，不走 GeckoLib 的命名约定

## 装备与武器

`registry/ModArmorMaterials` 注册护甲材质：绯色钼、苍穹、砷铅铁是钻石级，天门松是铁级；另有一份 `experiment_armor` 给试验物品 047~050 那套——护甲值与铁同级（2 / 6 / 5 / 2，合计 15）、每件 2 点韧性、附魔值 9、耐久倍率 15，维修材料是砷铅铁锭 / 绯红钼锭 / 䤧锭里的任意一样（`Ingredient.of` 收候选清单，命中一个就成立）

`registry/ModTiers` 注册工具分级，同样四份（绯色钼只有护甲，没有工具）

`registry/ModEquipment` 按套注册工具与护甲，六套共 54 件，按套存在 `Map<String, Set>` 里而不是逐件写字段。绯色钼的护甲带套装效果、物品在 `ModItems` 里，那一套只把已有 holder 拼进来

`item/ScarletMolybdArmorItem` 只有绯色钼一套用，四件穿齐时每件自己在 `inventoryTick` 里续生命恢复

绯色钼护甲的穿戴外观不用原版通用护甲层，用的是源 mod（twilightextra）的四件专用模型：`client/model/ScarletMolybdenumArmorModel` 把 `Modelcarmhelmett1`、`Modelcarmchestplatet1`、`Modelcarmleggingst1`、`Modelcarmbootst1` 四份 `LayerDefinition` 逐颗方块照搬，注册成四个 `ModelLayerLocation`，再按槽位拼进 `HumanoidModel`——对应部位换成专用部件（头 / 身 双臂 / 身 双腿 / 双腿），其余部位塞空 `ModelPart`，经 `RegisterClientExtensionsEvent` 的 `IClientItemExtensions#getHumanoidArmorModel` 交给原版 `HumanoidArmorLayer`。护甲层每帧先把玩家模型的姿势 `copyFrom` 到那个 `HumanoidModel` 的同名部位上，所以专用部件的基准位置必须与原版对齐（头、身 0,0,0；臂 ±5,2,0；腿 ±1.9,12,0），源模型的数值本来就是按这套基准写的。贴图是 `textures/entities/carmarmort1.png`（64×64，一件套一张，不分内外层），由 `ScarletMolybdArmorItem#getArmorTexture` 返回，顶掉 `ArmorMaterial.Layer` 拼出来的 `textures/models/armor/...`；原来那两张通用层贴图已经删除——它们的 UV 布局与这套几何对不上，正是之前通用模型看起来错位的原因。这一套也不接受护甲纹饰：锻造台那条靠标签挡（本套不在 `#minecraft:trimmable_armor` 里，纹饰配方的 base 匹配不上），已带 `TRIM` 组件的栈（命令、别的模组、老存档）由 `ScarletMolybdArmorItem#verifyComponentsAfterLoad` 在加载、网络同步与穿戴时摘掉——纹饰层是按原版块贴图排的，叠在 64×64 UV 的专用几何上只会错位。规格在 `gametest/EquipmentTests`：四件在内外两种层上都返回那张专用图、图本身在 jar 里；模板 + 红石 + 本套部件问不出任何锻造配方，带纹饰的栈从 NBT 走一趟回来组件消失

`item/CarminiteStaffItem` 近战按单手武器算，右键消耗一颗砷铅铁球发射一颗 `entity/CarminiteBallEntity`。六种附魔加成照搬源 mod 的换算（力量 火矢 冲击 多重 穿透 快速装填），物品挂在 `data/minecraft/tags/item/enchantable/` 的 `bow` 与 `crossbow` 两个原版标签上，否则附不上去

`entity/CarminiteBallEntity` 继承 `AbstractArrow`，命中后在上方一格留下 `entity/CarminiteFieldEntity`

`entity/CarminiteFieldEntity` 是普通的 `Entity`（不是生物），存活 75 tick，每 20 tick 对半径 4、高 ±1.5 的圆柱内生物结算一次伤害，每 tick 在边缘画一圈 12 个粒子。源 mod 把它做成了一只无 AI 的飞行生物，范围只能用方盒子，这里都不需要

`registry/ModParticles` 注册三种粒子（`particle_carminite_ball` `_trace` `_effect`），客户端 provider 在 `client/CarminiteParticle`

`registry/ModDamageTypes` 的 `carminite_field` 是反应场伤害，条目在 `data/dawnduskterminal/damage_type/`

这些内容从 `twilightextra`（MCreator 生成）迁移而来，原代码里的过程式实现已换成上面这些

装备贴图目前是原版铁、钻石贴图加色阶的占位图，真美术进来后换掉 `textures/item/` 与 `textures/models/armor/` 里对应前缀的文件即可

## 试验物品

序号表在 `registry/ModExperimentItems`：`TAKEN` 里列掉已经占号的（001 是能当合成台用的方块，在 `ModBlocks`；007 与 017 是两件近战伤害 4、攻速 5 的裸武器，共用 `meleeAttributes` 属性表、只挂属性不挂 tool tier（修饰符 ID 按物品 ID 分开，同一属性上两个同 ID 修饰符会互相顶掉，玩家底数是攻击力 1、攻速 4，所以面板 4/5 对应 +3/+1）；008 是 5 点饥饿、5 点饱和度的食物（系数 0.5，`FoodProperties` 存的是饱和度系数，点数 = 系数 × 饥饿 × 2）；020 是 20 点饥饿、20 点饱和度的食物，效果表是吃完那一刻现摇的（`item/Experiment020Item#rollEffects`：0~3 个、互不相同、各 10~300 秒、等级 I，池子里剔掉瞬发那三个、以及非原版里没有药水瓶的技术性效果（判据是扫 `BuiltInRegistries.POTION`：药水瓶显示的效果就取自这张表，原版不套这条，`bad_omen` 这类照旧留着），随机源当参数传进来好测，`FoodProperties#effects` 放不了随机表也不能放）；019 是一丛植物（`block/Experiment019Block` 继承 `BushBlock`，只站在 `#minecraft:dirt` 与耕地上、底下的土没了自己消失；方块模型 `block/experiment_019` 用 `minecraft:block/cross` 加 `"render_type": "minecraft:cutout"`（漏了这行透明像素会被涂成黑底，整丛变成一块黑方块；原版花的 `ItemBlockRenderTypes.setRenderLayer` 1.19 起废弃，模组方块一律写模型的 `render_type`），贴图直接引 `item/experiment_019` 那张，物品图标仍走 `item/generated`，与原版花一致；掉自身走 `loot_table/blocks/experiment_019.json`）；033/034 是两支突刺长矛，行为在 `item/ThrustSpearItem`；037~042 是钻石五件套与一件普通物品；043 是那台喷火器（`item/Experiment043Item`）；066 是那本恶魂陷阱书（`item/Experiment066Item`）；045 是那面盾（`item/Experiment045Item`，盾面在 `client/Experiment045ShieldRenderer`）；046 是砷铅铁钼反应权杖；047~050 是那套护甲（`item/ExperimentArmorItem`，见下面的「试验物品 047~050」）；051 是拆解台那台机器，方块在 `ModBlocks`、物品在 `ModItems`；104 是食物，在 `ModItems`；114 是能吃能摆的那一块，方块在 `ModBlocks`、物品在 `ModItems`；115 是暮色森林自带的，本模组不注册），剩下的空号按 `experiment_%03d` 补成占位简单物品，贴图统一借 `experiment_temp.png`。创造栏按序号升序排，115 从暮色森林的注册表里取，没装暮色森林就跳过；试验物品栏用 `withTabsBefore(dawnduskterminal:main)` 钉在主栏目后面——NeoForge 的 `CreativeModeTabRegistry` 对没声明 before/after 的 mod 栏目按注册名字母序排，注册先后不进排序，别的模组的栏目会插进本模组两个栏目中间

试验物品 032/033 是同一份 `item/ThrustSpearItem` 的两个注册。`getUseAnimation` 返回 `UseAnim.BOW`：第一人称的拉满过程与第三人称的双臂拉弓都由原版摆，物品模型必须配同一套变换——两张贴图是按弓的朝向画的，所以 `models/item/experiment_032.json`、`experiment_033.json` 照原版 `item/bow` 给了一份 `display`（thirdperson 的 [-80,260,-40] / [-80,-280,40] 与 -1,-2,2.5 的位移，firstperson 沿用 generated 那一套），挂 `item/generated` 不给变换会变成一张平片，挂 `item/handheld` 则会把矛按工具那样拿反。视野不缩：原版压 FOV 的条件在 `AbstractClientPlayer#getFieldOfViewModifier` 里，是 `getUseItem().is(Items.BOW)`，认物品本体，与非弓物品无关。`getUseDuration` 是 72000，按住右键不会自己结束；`onUseTick` 每 10 tick 取一次目标：`entity_interaction_range` 属性范围内、视线两侧各 30 度锥体里、有视线连通、离视线垂距最小的活生物，`hurt` 掉 1 点并放 `entity.player.hurt_sweet_berry_bush`。伤害类型直接用原版的 `minecraft:sweet_berry_bush`：普通伤害（护甲照减），它本来就在 `minecraft:no_knockback` 标签里，所以只有伤害没有击退；死亡信息与原版一致，本模组不另外写条目，物品也不带描述行。无敌帧不做任何手脚，两次伤害之间隔 10 tick，原版那套逻辑自己会算

试验物品 104 是 `ModItems` 里的一件普通 `Item`，只挂了 `Item.Properties().stacksTo(1).food(...)`：一个能吃两口，第一口的 `usingConvertsTo` 把剩下那半换成 `experiment_104_half`（走原版蜂蜜瓶换玻璃瓶那条路，`Player#eat` 里换栈），第二口把半块吃掉。每口 10 营养、0.6 饱和度系数（饱食度 10 × 0.6 × 2 = 12.0，等于两块烤马铃薯），两口合计 20 与 24.0。半块也是 `stacksTo(1)` 的食物，没有 `usingConvertsTo`；它不进试验物品栏（那栏只排 001~115 的序号），只能靠吃出来。半块的注册必须写在 104 前面——104 的 `usingConvertsTo` 在注册回调里就要取半块的物品栈，`DeferredRegister` 按插入顺序注册

试验物品 114 的方块就是暮色森林的试验物品 115 方块（`twilightforest:experiment_115`）的照抄：同一套状态 `omnomnom` 0~7 与 `regenerate`（名字都不改），同样的碰撞形状、比较器与红石输出公式，模型与侧、底、切口三张贴图直接用 115 的（`twilightforest:block/experiment115/*` 与 `twilightforest:block/blank`），顶面换成本模组自己的 `dawnduskterminal:block/experiment114_top`。不同之处：摆下来就是满的 8 片（115 的默认态是 `omnomnom=7`，只剩 1 片）、拿 114 点缺口一次补满（115 一次只补一片）、潜行收走只对全满的方块生效（115 吃到一半也能收）、吃掉一片额外随机放一声恶魂受伤、撒过红石粉那版不补片而改成收到一次红石信号随机放恶魂受伤

`item/Experiment114Item` 是 `BlockItem`，一份食物 32 营养、0.3 饱和度系数，就是 8 份 115（一份 4 与 0.3）。`useOn` 照 115：点在自己那一块上把交互让给方块，不是潜行就摆，摆不下才转成吃；吃完那一下在服务端随机音高音量放 `entity.ghast.hurt`

红石去重不靠额外的 `powered` 状态，而是一次方块刻：一次信号会引来多轮邻居更新，`neighborChanged` 里发现这一格已经排了方块刻就不再响，这样状态集与 115 完全一致。同理方块没有 `randomTicks()`——115 的随机刻只用于给 `regenerate` 那版补片，我们那版改成响声，随机刻无事可做

资源是 `blockstates/experiment_114.json` 的 16 个变体（`omnomnom` × `regenerate`）加 16 个 `models/block/experiment_114_<剩几片>_8[_regenerating].json`，模型内容从 115 的模型原样复制，只改两处：`_regenerating` 那八个的 `parent` 指到本命名空间，八个基础模型的 `top` 指到 `dawnduskterminal:block/experiment114_top`（`_regenerating` 只覆盖 `top_2`，顶面从父模型继承）

试验物品 051 是拆解台那台机器，方块属性照抄暮色森林 `twilightforest:uncrafting_table` 的注册参数（`ignitedByLava`、`mapColor(FIRE)`、`sound(WOOD)`、`strength(2.5)`，也一起进 `#minecraft:mineable/axe`），状态只有一个 `active`：有燃料在烧时换成 `_active` 那套贴图，`Properties.lightLevel` 按它给 13 级光（数字同原版熔炉的 `litBlockEmission(13)`；原版那个辅助方法写死了 `BlockStateProperties.LIT`，只能自己写一行），`animateTick` 照 `FurnaceBlock` 的音效段以 10% 概率放 `block.furnace.fire_crackle`——烟与火粒子没抄，因为原版是从 `FACING` 那一面喷的，本方块四面对称、没有朝向状态。与拆解台的功能差别（面板只有拆解、不收经验、不换配方、烧燃料、直接出货）写在 `block/entity/Experiment051BlockEntity` 的类注释里，机器刻骨架照原版熔炉的 `AbstractFurnaceBlockEntity#serverTick`，只是把「有没有活干」从一格结果换成 3x3 出料格（`canStore`），任何一格放不下就停工、进度清零

产物与拆解台同源靠三样东西：`crafting/UncraftingResolver` 按 `UncraftingMenu#getRecipesFor(ItemStack, Level)` 的顺序取第 0 条（先反查合成配方，再拆解配方，最后才是本方块自己加的熔炼回退），布局照拆解台按宽高铺到 3x3，`compat/TwilightUncrafting` 用注册表加反射读暮色森林的配方类型、`isItemStackAnIngredient`、`getCount` 与那一组白黑名单配置——源码里一个 `twilightforest.*` 的 import 都没有，缺 jar 也能编译。配方扫描比熔炉的 `CachedCheck` 贵，所以只在原料换了、数量变多了、不够当前配方吃了、`RecipeManager` 实例换了（数据包重载）时才重扫

只有拆解配方没有合成配方的物品（例如试验物品 017：四个钢叶加一片黑树树叶，`data/dawnduskterminal/recipe/experiment_017_uncrafting.json`）照暮色森林的 `twilightforest:uncrafting` 格式写：`input` 是能进机器的那件，`input_count` 是吃几个（省了就是 1），`key`/`pattern` 就是 3x3 产物网格，空格子不产东西；`cost` 是暮色森林自己那张拆解台收的经验等级，本方块不扣等级所以写 0 就行。这类配方要读暮色森林的类型，没装暮色森林时会在配方加载阶段报一条解析错误——暮色森林是硬依赖，可接受

界面是 `inventory/Experiment051Menu` 加 `client/Experiment051Screen`，面板 186x166（`textures/gui/experiment_051.png`，比原版熔炉宽一圈才放得下 3x3），火焰与箭头不自己画，直接 `blitSprite` 原版熔炉的 `container/furnace/lit_progress` 与 `container/furnace/burn_progress`，所以面板贴图里那两个空框的位置必须与菜单的 `FLAME_`/`ARROW_` 常量对齐。坐标走 `IMenuProviderExtension#writeClientSideData`（原版的开包不带坐标），菜单类型用 `IMenuTypeExtension.create`。漏斗顶面进原料、侧面进燃料、底面抽产物，合法性统一走方块实体的 `canPlaceItem`，界面与漏斗不会走岔

资源是 `blockstates/experiment_051.json` 两个变体加 `models/block/experiment_051[_active].json`（`cube_bottom_top`，顶/侧/底三张贴图各有 `_active` 版）、`loot_table/blocks/experiment_051.json`，物品模型指向方块模型。规格在 `gametest/Experiment051Tests`，机器刻是直接调 `serverTick` 跑完的，不靠 GameTest 等方块实体自然刻

试验物品 052 是照砷铅铁反应堆做的那颗方雷，方块在 `block/Experiment052Block`，状态只有一个 `active`：收到**任意一个**红石信号（`Level#hasNeighborSignal`，红石粉/红石块/贴面拉杆/中继器都算）就换成 `active`，模型换 `experiment_052_glow`、`Properties.lightLevel` 给 15 级光、放一声 `experiment_052_ignite`，再 `scheduleTick` 排 `FUSE_TICKS` = 60 tick（3 秒）的引信；`tick` 到点就炸，点着以后断电也停不下来（没有取消分支）。触发只认「从没点着变成点着」这一次，`onPlace` 也问一遍，所以摆在一片已经通着电的位置上会立刻点火。爆炸本体在 `world/CarminiteBlast#large`：走 `ServerLevel#explode` 那个带粒子与音效参数的十二参重载，粒子参数两档都填 `particle_carminite_blast`、音效填 `experiment_052_explode`——这个重载把两者塞进 `ClientboundExplodePacket` 一起发给客户端，服务端不再生成任何原版 `explosion`/`explosion_emitter` 粒子；随后本模组自己放两层粒子：先是一圈 48 颗的逆时针旋臂（`CarminiteBlast#ring`：按角度铺开、越外圈转过的角度越多，水平分量取 `(cos θ, -sin θ)`，速度 0.45→1.35，带竖向抖动），再撒一遍 `particle_carminite_blast`——与反应场那种 `particle_carminite_effect` **同一批八帧贴图**、只换一份更慢的帧序（`CarminiteParticle#blastProvider`，帧间隔 3 tick、寿命 20 tick，反应场那种保持原样），分布照原版爆炸的性子来：**落在球形范围里，半径 = 威力本身**（不乘系数，威力 6 → 6 格、威力 2 → 2 格），颗数随威力涨（威力 × 18，夹在 24~160 之间），每颗沿半径向外带一点**小**速度（0.05~0.20）；位置与初速都得一颗一颗算，所以走 `sendParticles` 的 `count == 0` 那条路。再补一颗烟花白闪 `minecraft:flash`、附近地面按威力补火（与原版一样跟威力走：威力 × 3 团，最少 3 团；撒开半径 = 威力、往下找 威力/3 格，威力 6 → 18 团），甩出 0~15 个碎片。爆炸范围内的同类方块会连锁，但一次最多五个：`CarminiteBlast#protectArmedBlocks` 挂在 `ExplosionEvent.Detonate` 上，主爆炸进行中先把 `getAffectedBlocks()`（就是 `Explosion#getToBlow()`）里**所有** `experiment_052` 摘掉——这种方块的爆炸不拆同类方块，范围内没被连锁到的那些就原样立着；再从摘下来的位置里挑离爆心最近的五个，把状态直接点亮并排一个 **1~10 tick 的随机**计划刻——跳过点火（不放音效、不等 3 秒引信），到点自己炸。每个被引爆的方块再走一遍同样的流程，于是连锁最多五路、每跳随机 1~10 tick，多出来的同类方块会被后续的连锁卷进去。另外每次爆炸（主爆炸与碎片的小爆炸都算）都会把附近刷成 `dawnduskterminal:old_growth_birch_forest`：走原版 `/fillbiome` 的那条实现 `FillBiomeCommand#fill`（按 4×4×4 单元改数据、会给附近玩家发包同步），半径 = 威力 × 2 格（最少 4 格），调用前先把半径覆盖的区块载进来，改失败就跳过、不抛异常。资源是 `blockstates/experiment_052.json` 两个变体加 `models/block/experiment_052[_glow].json`（`cube_all`）、`loot_table/blocks/experiment_052.json`，物品模型指向方块模型

砷铅铁火焰是 `block/CarminiteFireBlock` 继承原版 `BaseFireBlock` 而不是 `FireBlock`：蔓延、烧方块、按火烧掉可燃物全在 `FireBlock#tick` 里，`BaseFireBlock` 只留「火这个方块」该有的东西，`canBurn` 再钉死成 `false`。`entityInside` 整个换掉（原版那段会给 8 秒着火），只留接触掉 2 点，频率交给生物的受伤无敌时间，于是站在原地是每 0.5 秒 2 点；伤害类型是本模组的 `carminite_fire`，**只进 `#minecraft:no_knockback`、不进 `#minecraft:is_fire`**——进了 `is_fire` 会被 `Entity#isInvulnerableTo` 里的火焰免疫挡下来，而砷铅铁火焰要能烧穿烈焰人那类；`effects: burning` 只换受击音，不会点火。熄灭**照抄原版火焰**（`FireBlock#tick` 去掉蔓延）：每 30~39 tick 一个计划刻，淋雨按 `0.2 + age × 0.03` 的概率灭，`AGE` 每次长 0~1，没落在火源上又压不出可燃邻居时 `age > 3` 就灭——也就是压在石头上的原版火焰的寿命；支撑没了立刻掉，`onPlace` 不调 `super`（那会在主世界/下界顺手点出下界门）。模型是**原版火焰那一套**：状态有 `north`/`east`/`south`/`west`/`up` 五个朝向位加 `age`，方块状态是 `minecraft:fire` 同构的六个 multipart 段，十张模型 `carminite_fire_{floor,side,side_alt,up,up_alt}[01]` 各继承对应的 `template_fire_*`、只换 `fire` 那个贴图变量、`render_type` 写 `minecraft:cutout`；某面要不要画板子由邻居的实心面决定（`isFaceSturdy`），所以贴着墙、顶着天花板会铺满整格。环境音与烟都不抄原版，改成 `animateTick` 每次撒三颗砷铅铁余烬 `particle_carminite_ember`（客户端的 `animateTick` 一秒才轮到单个方块两三次，再叠概率就看不见了，原版火焰也是靠每次三颗烟撑起来的）。余烬是另开的一种粒子：贴图就是原版红石粒子那组 `minecraft:generic_0..7`，尺寸是原版红石粒子的 1.5 倍（全程不变，没有随年龄涨大那套）、恒满亮、八张贴图按年龄播成序列帧、负重力往上抬 + 横向阻尼（`client/CarminiteEmberParticle`）；初速自己写，不走 `Particle` 那个会塞 ±0.4 随机速度的构造。碎片拖的红线也用同一种。方块属性照原版火焰（`replaceable`、`noCollission`、`instabreak`、`WOOL`、`DESTROY`），光 10 级、`noLootTable`、没有物品形态，只能由 052 的爆炸与碎片生成

碎片是 `entity/CarminiteShrapnelEntity` 继承 `ThrowableProjectile`：重力（0.03）与空气阻力（每 tick ×0.99）由父类给，本类只剩两支回调。客户端注册的是 `NoopRenderer`，外观只有每 tick 一颗 `particle_carminite_ember`；打中生物是 1~10 点随机、`damageSources().explosion(this, 发射者)`（原版爆炸伤害），弹体不消失、同一只生物只吃一次；打中生物与打中方块都会在命中点放一次小型爆炸（`CarminiteBlast#small`：威力 2、`ExplosionInteraction.NONE` 不破坏方块、一个把 `shouldDamageEntity` 与击退都关掉的 `ExplosionDamageCalculator`、音效是原版那声 `entity.generic.explode`、粒子同一套、白闪一颗）并补几团 `carminite_fire`；打中方块之后弹体消失，100 tick 还没撞到任何东西也自己消失。规格在 `gametest/Experiment052Tests`：占位号让出、物品是那个方块的 `BlockItem`、15 级光、一个信号就点、引信跑满 3 秒后方块没了、火焰掉 2 点且不点火、火焰按原版年龄机制自己烧完、碎片打的是爆炸伤害、碎片落地补火、方块状态与十张模型是原版火焰那套

试验物品 065 是反建器那一档里更高级的一台（这一件同样是本模组自己的设计），方块在 `block/Experiment065Block`：没有状态、没有 ticker，物品是它的 `BlockItem`，方块属性照 052 那一档的塔木机器（3.5 硬度、石头音效、`COLOR_ORANGE`、进 `#minecraft:mineable/pickaxe`），`Properties.lightLevel` 常亮 15 级。它压的场是 17×17×17（`world/Experiment065Field` 的 `RADIUS` 8），判定只有一句：**伤害由谁打出**——取 `DamageSource#getDirectEntity()`（没有才退到 `getEntity()`），那个实体在场里就把这一下取消掉。近战里「直接实体」是攻击者本人，射弹里是那颗箭 / 火球，爆炸里是 tnt 那颗实体，所以场里射出去的箭飞出场以后照常伤人、场外射进来的箭飞到人身上时已经在场里反而打不动，这一条是「攻击源自范围内」的直接后果，规格里专门钉了一条；环境伤害（摔落、火焰、虚空、仙人掌、饥饿）来源上没有实体，场不做任何判定。取消挂在 `LivingIncomingDamageEvent` 上：它在 `LivingEntity#hurt` 里排在无敌帧判定之后、一切减伤之前，取消掉等于这一下从来没发生过——没有伤害、没有击退、没有受击音、不消耗护盾耐久、也不进无敌帧；客户端那份 `hurt` 也会发这个事件，所以先挡掉非服务端。场心不靠每 tick 扫 17³ = 4913 格：`world/Experiment065Field` 按维度存一张 `Set<BlockPos>`，方块实体的 `onLoad` / `setRemoved` 管区块载入与卸载，方块的 `onPlace` / `onRemove` 管摆下与挖掉（所以摆下那一 tick 就生效，不必等下一 tick 的方块实体装载），伤害事件只扫现存的场心；每次判定还会回头核一遍那一格还是不是本方块，方块实体那边撤登记真漏了也不会留下一个打不掉伤害的幽灵场，查之前先问 `ChunkSource#hasChunk`（`LevelReader` 那几个 `hasChunkAt` 在 1.21.1 已废弃），不会为了这个把区块读进来。方块实体没有 `ticker`：场是按位置查的，没有每 tick 要做的事。贴图只有 `experiment_065` 一张：方块自己发 15 级光，贴图直接读满亮，不再叠发光层（原先那套外胀 0.02 的双层 cutout 模型已经拆掉，`experiment_065_glow.png` 现在没有任何引用）。场的范围由 `world/CubeFieldMarker` 画出来，整段照暮色森林反建器（`twilightforest:antibuilder` 的 `AntibuilderBlockEntity#tick` / `makeOutline` / `drawParticleLine`，对照 4.8.3345 源码）写：红石粉（`DustParticleOptions.REDSTONE`）画框线，每 tick 在方块自己那一格里飘一颗粉尘，每 10 次挑一次连画三组框线；每组是两段——一条斜线（中心到某个角，或起始角到中心，随机二选一）加那个角所在的那条棱，十二条棱分顶面四条、底面四条、竖直四条；框的端点在 `pos ± RADIUS` 与 `pos + RADIUS + 1`，也就是方块中心往外各 8.5 格（`CubeFieldMarker#halfExtent`），正好压在场最外层那几格的外侧面上。与那边的三处差别只有：半径（那边 4，这里 8）、每条线的粒子数（那边一段固定 16 颗、棱长 5 格；这里按长度每格 3 颗，密度一致）、以及场里到底发生什么。粒子只在客户端画、且只在 16 格内有活着的玩家时画（那边同样是 `hasNearbyAlivePlayer(..., 16)`）：两个方块都把 `EntityBlock#getTicker` 覆成「客户端给 `CubeFieldMarker::tick`、服务端返回 `null`」，服务端一颗都不发、也不走 `sendParticles`资源是 `blockstates/experiment_065.json` 一个变体（没有状态）加 `models/block/experiment_065.json`（就是 `cube_all`）、`loot_table/blocks/experiment_065.json`，物品模型指向方块模型（占位物品原来那张 `minecraft:item/generated` 加 `experiment_temp` 的图标一并换掉）。规格在 `gametest/Experiment065Tests`：占位号已让出、物品是那个方块的 `BlockItem`、方块没有状态且常亮 15 级光、进了 `#minecraft:mineable/pickaxe`、场是 17 格（三条轴各在 8 格处打不动、9 格处打得动）、场里打场里与打场外都打不动、场外打进来照常、环境伤害不拦、射弹按弹体位置算、摆下当场生效而挖掉就停、模型是单层 `cube_all` 只有一张贴图、服务端没有 ticker

试验物品 069 是反建器那一档里更高级的第二台，与 065 共用 `world/CubeField` 那张 17×17×17 的场心表（按维度存 `Set<BlockPos>`、方块实体 `onLoad`/`setRemoved` 管区块载入卸载、方块 `onPlace`/`onRemove` 管摆下与挖掉、判定时回头核一遍那一格还是不是本方块），只是场里发生的事换了：**落在场里的方块更新不处理**。概念取自原版更新抑制器——更新链把 `CollectingNeighborUpdater` 的计数顶到上限之后，后来排进队列的更新整批丢掉；这台机器把「一段时间」改成「一个固定范围」。判定只有一句 `Experiment069Field#suppressed(level, pos)`，**看被更新那一格**：更新总是「某一格收到了一个更新」，收到更新的那一格在场里才丢掉，所以场里的变化照常会把更新送给场外的邻居。四个入口由三个 mixin 送进来：`mixin/BlockStateUpdateSuppressMixin` 挡 `BlockStateBase#handleNeighborChanged`（邻居更新唯一的投递口，`NeighborUpdater.executeUpdate` 由 `CollectingNeighborUpdater` 的三种 `NeighborUpdates` 调的就是它）、`#tick`（方块计划刻，`ServerLevel#tickBlock` 到点调它）与 `#randomTick`（随机刻，`ServerLevel#tickChunk` 选中就调它）；`mixin/LevelShapeUpdateSuppressMixin` 挡 `Level#neighborShapeChanged`（形状更新的排队口，`BlockStateBase#updateNeighbourShapes` 按六个方向逐个调它，挡在这里而不是挡 `BlockStateBase#updateShape`，后者还被结构模板落地与区块升级数据直接调，挡它会把那两条路线一起挡歪）；`mixin/FluidStateUpdateSuppressMixin` 挡 `FluidState#tick` 与 `#randomTick`（水与岩浆的流动走 `ServerLevel#tickFluid` 那条流体队列，与方块刻不是一条路）。挡在**执行口**而不是排队口，就是更新抑制器本来的面貌：更新照样排进队列，排到场上时被丢掉。没有场的地方每条只花一次 `HashMap` 查表——邻居更新与随机刻都是每秒上千次的热路径，这里不能有更贵的东西。方块属性与 065 同档，地图色用 `TERRACOTTA_ORANGE` 与 065 的 `COLOR_ORANGE` 分开；资源是 `blockstates/experiment_069.json` 一个变体加 `models/block/experiment_069.json`（与 065 一样就是 `cube_all`，单层一张贴图）、`loot_table/blocks/experiment_069.json`，物品模型指向方块模型；场有多大同样由 `world/CubeFieldMarker` 那套红石粉框线画出来（与 065 同一份，客户端画、16 格内有玩家才画、服务端不挂 ticker）。规格在 `gametest/Experiment069Tests`：占位号已让出、物品是那个方块的 `BlockItem`、方块没有状态且常亮 15 级光、进了 `#minecraft:mineable/pickaxe`、场是 17 格，邻居更新（红石灯通着电不亮）、形状更新（栅栏不连）、计划刻（沙不落）、随机刻（冰不化）、流体刻（水不流）各钉一条且各带场外对照，摆下当场生效而挖掉就停，模型是单层 `cube_all`、立方框的八个角在中心外各 8.5 格、服务端没有 ticker；这些测试**一条一个 batch**——场是 17 格、测试结构只有 16 格，一台机器压的场必然伸到自己的结构外面，而同一个 batch 里的测试是并行的（阵位只隔 5 格），挤在一起会互相压更新，之前 052 的连锁测试正是这样被压红的

试验物品 004 是照熔炉工作的那台机器，方块继承原版 `AbstractFurnaceBlock`，状态就是熔炉那对 `facing` 与 `lit`，摆放朝向、旋转镜像、比较器、开界面都走原版抽象层；方块实体没有继承 `AbstractFurnaceBlockEntity`：它的 `serverTick` 是静态方法、每锅的时间由私有方法按配方现算，两处都没有留给子类的口子，而「烧食物快一倍」恰恰要改这两处，所以抄了一份三格容器骨架（`DataComponents.FOOD` 的产物按配方时间的一半算，200 → 100），四个数据槽的索引与熔炉完全一致，界面因此能直接复用原版那套。界面是 `inventory/Experiment004Menu` 继承 `AbstractFurnaceMenu` 加 `client/Experiment004Screen` 继承 `AbstractFurnaceScreen`，面板贴图与火苗、箭头两个 sprite 都用原版熔炉的，配方书跟着原版走。菜单类型单独注册 `experiment_004`：原版 `FurnaceMenu` 把 `MenuType.FURNACE` 写死在构造参数里，继承它的话客户端会按熔炉界面开，本模组的类型与屏幕就白注册了；方块坐标不往客户端传，原版熔炉界面本来就拿空容器、内容靠槽位同步。产物格换成自己的 `ResultSlot`：原版 `FurnaceResultSlot` 只在容器是 `AbstractFurnaceBlockEntity` 时才结算经验，这里改成按 `recipesUsed` 结算，取产物与敲方块时撒经验，行为与熔炉一致。不产生粒子：熔炉的烟与火写在 `FurnaceBlock#animateTick` 里，本方块不是那一支，只在 `Experiment004Block#animateTick` 补了一成概率的火响。资源是 `blockstates/experiment_004.json` 八个变体（`facing` × `lit`）加 `models/block/experiment_004[_active].json`（`minecraft:block/orientable`，正面 `experiment_004_forward[_active]`，其余五面同一张 `experiment_004`）、`loot_table/blocks/experiment_004.json`，物品模型指向方块模型；规格在 `gametest/Experiment004Tests`

试验物品 027 是那支画红石点的笔：`item/Experiment027Item` 按住右键不松（`getUseDuration` 72000，`use` 里两侧都 `startUsingItem`），`onUseTick` 每 tick 在服务端发一个粒子。落点由 `Player#pick(blockInteractionRange, 1.0F, false)` 取：射线走方块轮廓，打中方块就用 `BlockHitResult#getLocation()`，再沿命中面法线让出 0.02 格，免得粒子半边陷进方块里；看空或超出 `block_interaction_range` 属性（默认 4.5 格）时 `pick` 是 MISS，什么都不发。粒子类型是 `ModParticles#EXPERIMENT_027`（`SimpleParticleType`），客户端 provider 在 `client/Experiment027Particle`：继承 `TextureSheetParticle`、贴图表 `particles/particle_experiment_027.json` 直接借原版红石粒子那组 `minecraft:generic_4`，颜色按原版 `DustParticle` 的算法乘成红色，`quadSize` 在原版红石的 0.75 基准上再乘 0.5，初速 0、重力 0、`hasPhysics` 关掉，`lifetime` 写死 1200 tick（60 秒）——不用原版 `ParticleTypes.DUST` 就是因为它的寿命与随年龄收缩的尺寸都写死在 `DustParticleBase` 里。规格在 `gametest/Experiment027Tests`：占位号已让出、粒子类型注册过、正对石头时落点落在那个面上、看空与超距都是 null

试验物品 043 是那台手持喷火器：单击右键，把暮色森林封装的喷火器（`twilightforest:encased_fire_jet`）从接过红石信号到冷却下来的整套过程沿视线放出去，逐拍对齐写在 `item/FireJetProcess` 里。方块那套在 `EncasedFireJetBlock#neighborChanged` 里从 IDLE 切到 POPPING 并放 jet_start（`block.comparator.click`，音量 0.3、音高 0.6），`FireJetBlockEntity#tickPopping` 在计数 20/40/60 各放 8 颗 `LAVA` 加一声 `block.lava.pop`，到 80 切 FLAME；`tickFlame` 跑 61 拍（计数 1..61，第 61 拍溢出归零后还处理一拍）：第 1 拍再放 jet_start，每 2 tick 一颗大烟加五路大火焰（中心一路、上下左右各偏 1 格，初速沿轴 0.75、两侧各带 0.05 向心），每 4 tick 一声 jet_active（`entity.ghast.shoot`），每 5 tick 对伤害立方体里的活物造成 2 点并点燃 300 tick，最后切 TIMEOUT。这里整段照抄，只把「方块朝上」换成「玩家的视线」：枪口是眼睛往前 1 格，右/上轴由视线算出来，四路火苗按这对轴摆，伤害立方体也一起转（沿轴 14 格、半径 2 格）；**枪口与朝向每 tick 重新绑到玩家身上**（`refresh`），走开、转身火跟着走、跟着转，玩家下线/死亡/换维度就断掉；施放期间把点火那只手换成别的东西，剩下的火改成从玩家**脚下**朝正上方喷、连玩家自己一起烧（那一档不再把射手排除在伤害判定外），换回 043 就恢复按视线喷。伤害抬到 4 点（方块是 2 点），点燃仍 300 tick，火焰免疫跳过、不看遮挡（同方块），射出者是玩家；伤害类型进了 `#minecraft:bypasses_cooldown`，所以每拍都真打进去，不会被受击后的无敌时间挡住。过程挂在 `ServerTickEvent.Post`（`DdtServerEvents`）上推进，所以单击一次就够，松手、换物品都不打断；不用 `startUsingItem`。大火焰粒子是 `particle/JetFlameParticleOptions` + `JetFlameParticleType` + 客户端 `client/JetFlameParticle`：尺寸是原版火焰粒子的五倍、随寿命收缩到一半、寿命 12~44 tick、速度逐 tick 衰减 0.96——原版粒子包只能给客户端现摇随机速度，所以初速塞进粒子选项里一起发。物品 64 点耐久、喷一次扣 1 点；冷却在启动那一刻挂 `TOTAL_TICKS + 60` = 201 tick（等价于「跑完再冷却 3 秒」）。伤害类型 `dawnduskterminal:experiment_043`（`data/dawnduskterminal/damage_type/experiment_043.json`，`effects: burning`）与暮色森林的 `fire_jet` 一样进了 `#minecraft:no_knockback`、`#minecraft:is_fire` 与 `#minecraft:ignites_armor_stands`，另加 `#minecraft:bypasses_cooldown`，死亡信息是 `death.attack.experiment_043`。规格在 `gametest/Experiment043Tests`：预热恰好三次、火焰 61 拍与 13 拍伤害、141 tick 后自己停、猪（无甲）每拍掉 4 点并点燃 300 tick、第二拍照样打得进无敌帧、14 格内挨/14 格外不挨、中途挪位置转身火跟着走、施放期间换走物品就切到脚下往上喷并烧到自己、背后/侧面/烈焰人都挨不着、单击扣 1 点耐久并挂冷却

试验物品 066 是那本恶魂陷阱书：单击右键把暮色森林恶魂陷阱方块（`twilightforest:ghast_trap`）压场那一段沿视线放出去，实现在 `item/GhastTrapProcess`。方块那套在 `GhastTrapBlockEntity#tickActive` 里：场是以方块为中心横 6 格、纵 16 格的立方，场里的生物**每 tick** 被 `setDeltaMovement` 朝场心拽（`(场心 - 生物) * 0.1`），随机十分之一概率挨一下（10 点），跑 120 tick 自己关掉，充能靠 `tickInactive` 数场边死掉的砷铅铁恶魂幼体。这里去掉充能，场换成「从眼睛往前 16 格长、轴线外 4 格粗的走廊」：判定把活物包围盒中心投到「视线轴 × 右 × 上」三条轴上（与 043 同一套写法），沿轴落在 0~16、两条横轴都在 4 格加包围盒半径以内就算在场里；牵引照抄 0.1 那一条、不封顶，方向朝着场心。伤害是**每 tick 调一次 `hurt`**，不排节拍：原版 `LivingEntity#hurt` 那套无敌帧（打出一下后 `invulnerableTime` 从 20 往下减，掉到 10 之前再打不算）会把连续施压自动压成每 0.5 秒一下，120 tick 正好 12 下，所以伤害类型**不进** `#minecraft:bypasses_cooldown`——绕过去就把节奏弄没了（043 的喷火器反过来要绕，因为它是逐拍的独立攻击）。对象是 `LivingEntity` 全体（怪物、动物、玩家都会挨拽挨打，这本书要能装到敌怪身上去打玩家）加 `ItemEntity`（掉落物只挨拽不掉血），只排除施放者自己与观察者；施放者类型就是 `LivingEntity`，怪物拿着调用 `GhastTrapProcess#start` 就能用。伤害类型 `experiment_066` 只进 `#minecraft:no_knockback`，不然伤害自带的击退会和牵引互相抵消。**暮色恶魂的免伤不是标签能解的**：`UrGhast#hurt` 里 `if (isInTantrum()) amount /= 10`，退出 tantrum 要累计实扣 18 点，书每下 7 点打两下就攒满、之后每下只剩 0.7（探针实测 40 tick 只扣 15.4）；`isInvulnerableTo` 只认 `IN_WALL` / `IS_FIRE` / `invulnerable` 旗标，我们这条类型本来就不沾。所以命中前用 `item/UrGhastPhaseSuppressor` 反射调它的 `setInTantrum(false)` + `resetDamageUntilNextPhase()`（恶魂陷阱方块 `tickActive` 同款，只是它每 tick 还调 `setInTrap()`），把它压在相位外，每下都满额；反射按类名 `twilightforest.entity.boss.UrGhast` 找，缺 jar 或方法改名就静默降级成普通伤害，守住「不 import 暮色森林、缺 jar 仍可编译」这条。粒子与音效不是自己挑的，整份照抄那块方块的客户端分支：一拍五颗 `twilightforest:ghast_trap`（`vx = cos(counter/10) * 2.5`、`vy = 20`、`vz = sin(counter/10) * 2.5`，五颗分别是 `(vx,vy,vz)`、`(-vx,vy,-vz)`、`(-vx,vy/2,vz)`、`(vx/2,vy/4,vz/2)`、`(-vx/2,vy/4,-vz/2)`，；方块那三个偏移里 `vy = 20` 是「吸口向」、朝世界 Y（所以方块是从二十格高空往下收），本书的吸口朝视线，这一份喂给 `direction`、另外两份喂给右轴与上轴，臂因此从视线远处收进吸口；速度即出生偏移，粒子从「场心 + 偏移」收进场心），`counter > 100` 之后每 4 tick 补一颗 `twilightforest:huge_smoke`（偏移 `cos*0.05, 0.25, sin*0.05`）；这些粒子从服务端发，包里的 `count` 必须是 0——客户端那条分支走 `addParticle(type, x, y, z, maxSpeed * xDist, maxSpeed * yDist, maxSpeed * zDist)`，速度按原值落地不掺随机，正好对上方块客户端那六个参数。场心不是方块，是**施放者身上那个点**：眼睛往前 1 格（`TRAP_OFFSET`），每 tick 跟着视线转，与 043 的枪口同一个做法——所以走廊里的东西是朝**施放者**被吸过去的，五颗一轮的粒子与三支音效也都从这一个场心出。音效三支 `block.twilightforest.ghast_trap.warmup` / `.on` / `.spindown`，与方块一样逐拍放：0~29 tick 预热、30~79 通电、80 以后收场，音量 1、音高 4，`SoundSource.BLOCKS`；两个资源都只按资源 ID 从 `BuiltInRegistries` 取，不 import 暮色森林。场每 tick 重新绑到施放者的眼睛与视线（转身就跟着转，人死、换维度就断），过程挂在 `ServerTickEvent.Post`（`DdtServerEvents`）上推进，冷却 180 tick（整段 120 再加 3 秒），一次扣 1 点耐久、共 64 点。规格在 `gametest/Experiment066Tests`：占位号已让出、64 点耐久且不可堆叠、冷却第 179 tick 还锁着而第 180 tick 解开、走廊里的目标 120 tick 落下 12 下（100 → 16）、背后 / 侧面 / 16 格以外不挨、玩家在走廊里照样掉血而施放者自己不掉、`twilightforest:ghast_trap` / `huge_smoke` 与三支音效都取得到、模型与三条语言键、伤害类型只在 no_knockback 而不在 bypasses_cooldown

试验物品 045 是一面盾：`item/Experiment045Item` 直接继承原版 `ShieldItem`，格挡、每挡一次掉 1 点耐久、可用木板修、只进副手、72000 tick 的 `UseAnim.BLOCK` 都是原版那一套；336 点耐久与 `Items.SHIELD` 相同，`BANNER_PATTERNS` 也照原版给了默认空表。物品模型 `item/experiment_045` 挂 `minecraft:builtin/entity`（`gui_light: front` 加原版盾那套 `display`），并按原版盾的 `blocking` 谓词切到 `item/experiment_045_blocking`——这条谓词原版只注册给 `Items.SHIELD`，客户端 setup 里给 045 补了一条，举盾时的第一/第三人称变换才换得过去。几何体直接用原版 `ShieldModel`，真正画盾面的是 `client/Experiment045ShieldRenderer`（`BlockEntityWithoutLevelRenderer`，经 `RegisterClientExtensionsEvent` 挂到物品上）：没有旗面图案时用 `item/experiment_045_nopattern` 整面木盾，有图案或底色时换成 `item/experiment_045_base`（正面留白）并交给 `BannerRenderer#renderPatterns` 一层层叠；发光层 `item/experiment_045_glow` 画在同一块正脸上，走 `RenderType#entityTranslucentEmissive`，并单独注册成固定渲染缓冲（`RegisterRenderBuffersEvent`）——固定缓冲按登记顺序冲刷，它排在原版那几条之后，所以发光层一定叠在本体与旗面图案之上，不会被自己盖住。三张贴图都在 `textures/item/` 下，被原版 blocks 图集的目录源收进去，`Material` 直接指方块图集。规格在 `gametest/Experiment045Tests`：占位号已让出、是 `ShieldItem` 子类、336 点耐久且不可堆叠、`SHIELD_BLOCK` 能力放行且只进副手、木板可修、模型是 `builtin/entity` 且带 `blocking` 覆盖、三张贴图都在 jar 里

试验物品 047~050 是一套护甲（047 头盔、048 胸甲、049 护腿、050 靴子），物品是 `item/ExperimentArmorItem`，只为重写两处方法而存在：`getArmorTexture` 把护甲层的贴图指到 `textures/entities/experiment_armor.png`，`verifyComponentsAfterLoad` 摘掉 `TRIM` 组件（理由同绯色钼那套：纹饰层按原版 64×32 排，叠在这套 UV 上只会错位；锻造台那条本来也进不来，本套不在 `#minecraft:trimmable_armor` 里）。数值在 `ModArmorMaterials#EXPERIMENT_ARMOR`：护甲值与铁同级、每件 2 点韧性、附魔值 9、耐久倍率 15。维修材料是三样锭里的**任意一样**——`Ingredient.of` 收的是候选清单，`Ingredient#test` 命中其中一个就成立，砷铅铁锭、绯红钼锭、䤧锭随便哪一样都能修，不需要凑齐三样

穿戴外观来自源文件那一份基岩版几何（`minecraft:geometry` 1.12.0，64×64 的 UV，头 胸 双臂 双腿 双脚八根骨骼），四件共用，`client/model/ExperimentArmorModel` 按槽位拆成四份 `LayerDefinition`：头盔取头、胸甲取躯干与双臂（含两根倾角 22.5° 的上臂筒与肩外侧甲片）、护腿取双腿、靴子取双脚（含一块转 90° 的脚背斜护片）。基岩版几何搬进 `PartDefinition` 的换算三条：基岩原点在脚底 y 朝上、java 模型 y 朝下且头部枢轴在 y=0 对应基岩 y=24（骨骼位姿 `(px, 24-py, pz)`，方块最小角 `(bx-px, py-by-sy, bz-pz)`）；带 `rotation` 的方块拆成骨骼下的子 `ModelPart`；**旋转角度照搬基岩版的符号，不取负**——肩甲那对 22.5° 与脚背护片那对 ±90° 都是右正左负（左件带 `mirror`），取负会让它们朝里歪。算出来与原版玩家模型的基准位置完全对得上（头身 0,0,0、臂 ±5,2,0、腿 ±1.9,12,0，臂腿的方块也是原版那几组数），因为那份几何本来就照原版人体比例写。姿势与可见性不用自己算：`IClientItemExtensions#getGenericArmorModel` 会把护甲层刚算好的那套 `copyModelProperties` 拷到缓存的模型上

发光层走 `mixin/ExperimentArmorGlowMixin`：原版护甲层一件物品只画一遍，`renderArmorPiece` 里那个循环绕 `ArmorMaterial#layers()` 走、每层都交给 `renderModel`，所以在 `renderArmorPiece` 的 TAIL 再补一遍，同一份几何换 `textures/entities/experiment_armor_glow.png`。渲染类型必须**还是** `armorCutoutNoCull`：它带 `VIEW_OFFSET_Z_LAYERING`，冲刷时把模型视图朝相机缩 0.99975586，本体因此在深度上比真实位置更近约万分之二点四；换成没有这个偏移的 `entityTranslucentEmissive`，同一份几何摆在同一处会永远落后于本体，整层被盔甲盖住，只有把相机钻进模型里才在轮廓边缘漏出一丝（这就是之前那一版的毛病）。两个批次用同一个 RenderType 状态，深度才相等、`LEQUAL` 才过，而发光层是后冲刷的那一批，于是稳稳压在本体之上。「发光」不靠 emissive 着色器而靠顶点光：`rendertype_armor_cutout_no_cull` 的 vsh 里 `texelFetch(Sampler2, UV2 / 16, 0)` 直接采样顶点光照图，传 `LightTexture.FULL_BRIGHT` 进去整层就恒为满亮；它 fsh 里 `color.a < 0.1` 直接 discard，发光图透明的地方不会糊黑。注在单个槽位那一层而不是 `render` 上，穿一件还是四件都不用另写分支。规格在 `gametest/Experiment047Tests`：占位号已让出、四件各占对槽位、护甲值 2/6/5/2 与总计 15、韧性 2、附魔值 9、耐久照铁、三样锭逐样单独都能修而铁锭钻石不行、四件的内外层都返回那张专用图、本体图与发光图都在 jar 里、四个物品模型各指自己那张图标


## 装饰画

变体是数据包注册表，条目在 `data/dawnduskterminal/painting_variant/painting_ghast_1.json`，3 格宽 2 格高；`registry/ModPaintingVariants` 只放指向它的 `ResourceKey`，尺寸不在 Java 侧重复

贴图放 `assets/dawnduskterminal/textures/painting/painting_ghast_1.png`。原版 `atlases/paintings.json` 只声明了「扫 `textures/painting` 目录」，那个目录源跨命名空间收图，所以放进这个位置就会被 `minecraft:paintings` 图集收进去，渲染走原版 `PaintingRenderer`，不用自己写

物品图标的贴图必须另放一份到 `textures/item/`：1.21.1 的物品模型贴图是从 `minecraft:paintings` 之外的 `blocks` 图集取的，而那个图集只声明了 `block/` 与 `item/` 两个目录源，`textures/entity/` 下的图不会被收，模型就会渲染成紫黑格子（`Missing textures in model ...`）

`entity/GhastPaintingEntity` 是独立的实体类型，不共用 `minecraft:painting`：原版画物品只从 `#minecraft:painting_variant/placeable` 挑变体，我们的变体不进那个标签，于是原版物品放不出它；这个实体又把 `setVariant` 固定成自己的变体，敲碎掉的是 `painting_ghast_1` 物品，中键也是它

`mixin/PaintingVariantGuardMixin` 堵的是手改那条侧门：原版画实体上的 `setVariant` 遇到这个变体直接吞掉，`/summon` 指定变体与旧存档里的 NBT 都改不出我们的画

`item/GhastPaintingItem` 的摆放流程照抄 `HangingEntityItem`，只能放这一幅，没有配方

## 维度与晨昏线

`registry/ModDimensions.CHRONO` 是维度的 `ResourceKey`

`world/ChronoLine` 是 record，存原点与单位法向

`ChronoLine.distance(x,z)` 是点到线的有符号距离

`ChronoLine.param(x,z)` 是距离除以最大距离后钳到 -1 到 1

`ChronoLine.create(random)` 每个世界随机一次法向，基准朝东，抖动正负 30 度，再随机决定是否转 180 度朝西

配置 `normal_override` 可固定法向

`world/ChronoLineState` 继承 `SavedData`，存在主世界，键 `dawnduskterminal_chrono_line`

服务端与客户端各有一份静态缓存 `serverLine` 与 `clientLine`

`network/ChronoLinePayload` 在玩家登录时把线下发到客户端

法向只按维度算一次，多个玩家共用同一条线

## 群系源

`world/PaletteBiomeSource` 是自写 `BiomeSource`，注册为 `dawnduskterminal:palette`

它要求一个 `delegate` 与一个 `palette` 群系标签

`preset` 是原版主世界参数表，`extra` 是追加在表尾的自建气候点（极昼极夜），选格用原版那套 `ParameterList.findValue`

`getNoiseBiome` 先跑 delegate，再把 `minecraft:*` 群系换成同名副本

副本名与命名空间对应关系按路径匹配，例如 `minecraft:plains` 换成 `dawnduskterminal:plains`

`extra` 的条目按温度出界来写：原版参数表只覆盖 `[-1, 1]`，出界的跨度一写就赢。温度由 `ChronoTemperature` 在极带推到 `±2`

一格在替换表里再改指：`minecraft:desert` 换白地（昼区）。这只是决定那一格装哪个副本，选格仍然是气候表算的

`floatrees` 群系走另一条路，采样深度低于阈值时直接返回

群系源里没有任何按 `param` 判群系的特例，极带暂时与相邻带同群系

替换表延迟构建，因为 biome source 在数据包解析期创建，那时标签里的 holder 还没有 key

细节见 `worldgen.md`

## 世界生成补丁

数据包改不到的原版硬编码由 `mixin/` 接管

`LavaBandPaletteMixin` 把 `createFluidPicker` 的 y 以下岩浆带换成热情的暮光

`LavaAquiferPaletteMixin` 把深部岩浆含水层换成热情的暮光

`PillarsFluidBlockMixin` 让石柱洞穴保持干燥

`NoiseChunkVeinPaletteMixin` 用 `world/ChronoVeinPalette` 把大矿脉的花岗岩与凝灰岩换成赭石与暮光石

温度的晨昏线偏移不在这里，走 `world/PaletteBiomeSource` 与 `world/ChronoTemperature`，见「世界生成补丁」

`MineshaftHeightMixin` 把废弃矿井压到 y -64 到 -10

`MineshaftChestLootMixin` 把矿井箱车战利品换成 `dawnduskterminal:chests/chrono_mineshaft`

识别维度统一靠噪声设置的 `default_fluid` 是不是 `portal_fluid`

## 传送门

`portal/PortalShape` 做水池识别与判定

`portal/PortalBuilder` 把水池方块替换成传送门流体

`portal/PortalTrigger` 判定实体是否处在可用传送门里

`portal/PortalBlock` 是传送门流体的方块，处理方块内实体与自愈

`portal/PortalTeleporter` 负责即时传送 落点搜索 到达门

`portal/PortalState` 是玩家附件，存回程维度与坐标

细节见 `portal.md`

## 流体

`registry/ModFluids` 注册 `portal_fluid`，流动参数照抄水

`registry/ModScorchingTwilight` 注册 `enthu_fluid`（热情的暮光）的类型 流体 方块 桶

`world/FluidReactions` 在 `FMLCommonSetupEvent` 上把接触反应注册进 NeoForge `FluidInteractionRegistry`

`block/ScorchingTwilightBlock` 继承岩浆的方块属性，但不点火不清植物，接触造成 6 点 `enthu_fluid` 伤害

`item/CanocanItem` 是唯一能舀这两种流体的容器，原版桶会被毁灭

## 进度与效果

`progression/BossKind` 枚举三个暮色森林 Boss 与对应战利品

`progression/BossProgress` 是 record，存三个击败标记

`progression/BossProgressTracker` 在击杀事件里写入附件

附件定义在 `registry/ModAttachments`，`boss_progress` 序列化并 `copyOnDeath`

`registry/ModEffects` 注册 `stability` 与 `portal_sickness`

`world/StabilityEffect` 是纯标记效果，方向由 `PortalBlock` 决定

`advancement/LineParamTrigger` 是自定义判定，按晨昏线投影参数给进度

## 客户端

`client/DdtClient` 是客户端入口，注册模型 颜色 渲染层 事件

`client/ChronoFoliageColors` 复刻暮色森林的树叶染色算法

`client/model/MountedTowerWoodModel` 做镶边塔木与封装塔木梁的连接模型

`client/PortalFluidExtensions` 与 `ScorchingTwilightFluidExtensions` 是流体的客户端扩展

`DdtClient` 注册方块与物品颜色，草与树叶随群系染色

天空全部交给原版渲染器，维度特效直接复用 `minecraft:overworld`，见「天空」

## Mixin

配置 `dawnduskterminal.mixins.json`，分公共与客户端两份

`FluidStatusAccessor` 暴露 `Aquifer.FluidStatus` 的流体类型给其他 mixin

`LavaBandPaletteMixin` `LavaAquiferPaletteMixin` `PillarsFluidBlockMixin` 见「世界生成补丁」

`NoiseChunkVeinPaletteMixin` 见「世界生成补丁」

`MineshaftHeightMixin` `MineshaftChestLootMixin` 见「世界生成补丁」

`FeatureSorterMixin` 替换原版特征排序为可容忍环的实现，受配置 `tolerate_feature_cycles` 控制

`LightEngineSkyLightMixin` 在晨昏线维度把天光上限压到 `ChronoSkyLight.maxSkyLight`

`StructureExclusionMixin` 在晨昏线维度只放行结构白名单里的结构

`BiomeColorsFoliageMixin` 是客户端 mixin，把树叶染色接进 `ChronoFoliageColors`

`ClientLevelTimeOfDayMixin` 与 `LevelRendererSkyAlignMixin` 是客户端 mixin，见「天空」

`ClientLevelSkyColorMixin` 是客户端 mixin，见「天空」

## 天空

自绘天空已移除，天空全部走原版 `LevelRenderer.renderSky`

维度特效直接复用 `minecraft:overworld`，雾色 日月 星空 日落光晕 云都是原版的，`dimension_type/chrono.json` 的 `effects` 就不再是模组自己的键

`ClientLevelTimeOfDayMixin` 在晨昏线维度把 `getTimeOfDay` 换成 `0.25 - param * sun_angle_max / 360`，原版那一整套渲染因此跟着玩家到晨昏线的距离走，太阳仰角仍是 `param * sun_angle_max`

原版日月轨道永远落在 X 轴上，`LevelRendererSkyAlignMixin` 在 `renderSky` 的两处 `pushPose` 之后垫一层绕世界 Y 轴的旋转，把轨道与日落光晕转到晨昏线法向上

`fixed_time` 仍留在维度类型里，它只影响服务端的 `getTimeOfDay`，客户端渲染不再读它

`ClientLevelSkyColorMixin` 把晨昏线维度的 `getSkyColor` 乘 0.75。原版日月是加色混合，而极昼侧自定义群系的 `sky_color` 是近白色（`white_land` 为 `FFF9F0`），加到纯白上还是纯白，太阳就和天空糊成一片。天穹色同时决定雾色，所以这一下也把过曝的雾降了下来。常数在 mixin 里，调小更暗

## 事件

`server/DdtServerEvents` 挂到 `NeoForge.EVENT_BUS`

`onServerStarted` 读取并记录晨昏线，校验结构与摆放表

`onPlayerLoggedIn` 下发晨昏线包

`onPlayerTick` 每 10 tick 扫玩家附近的投料物，进维度后触发进度判定

`onLivingDeath` 记录 Boss 击杀

`onRightClickItem` 拦截原版桶，禁止舀两种模组流体

## 结构

`structure/StructureExclusions` 是唯一白名单，`StructureExclusionMixin` 排除不在其中的结构

`structure/StructureExclusions` 里的 `UNMAPPED` 与 `LANDMARKS` 记录未放行项与地标，只服务日志

服务端启动时校验这几张表，缺失只记日志

## 已知遗留

`registry/ModEffects` 仍注册 `portal_sickness`，但已无人施加，方向改由 `stability` 决定

`config/DdtConfig` 的 `portal.cooldown_ticks` 与 `portal.entity_debuff` 已无读取方

语言文件里 `death.attack.dawnduskterminal.portal` 与 `message.dawnduskterminal.searching` 已不再使用
