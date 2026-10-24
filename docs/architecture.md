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

`server/` 服务端事件与刷怪规则

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

`registry/ModEntities` 注册 `tower_ghast`（高塔恶魂），属性走 `EntityAttributeCreationEvent`

`entity/TowerGhastEntity` 由 GeckoLib 驱动，模型 动画 贴图从 Yes Steve Model 的 Bedrock 文件转换而来，转换与校验在 `tools/ysm/`

`client/TowerGhastRenderer` 把三条资源路径直接写出，不走 GeckoLib 的命名约定

## 装备与武器

`registry/ModArmorMaterials` 注册护甲材质，目前四份：绯色钼、苍穹、砷铅铁是钻石级，天门松是铁级

`registry/ModTiers` 注册工具分级，同样四份（绯色钼只有护甲，没有工具）

`registry/ModEquipment` 按套注册工具与护甲，六套共 54 件，按套存在 `Map<String, Set>` 里而不是逐件写字段。绯色钼的护甲带套装效果、物品在 `ModItems` 里，那一套只把已有 holder 拼进来

`item/ScarletMolybdArmorItem` 只有绯色钼一套用，四件穿齐时每件自己在 `inventoryTick` 里续生命恢复

`item/CarminiteStaffItem` 近战按单手武器算，右键消耗一颗砷铅铁球发射一颗 `entity/CarminiteBallEntity`。六种附魔加成照搬源 mod 的换算（力量 火矢 冲击 多重 穿透 快速装填），物品挂在 `data/minecraft/tags/item/enchantable/` 的 `bow` 与 `crossbow` 两个原版标签上，否则附不上去

`entity/CarminiteBallEntity` 继承 `AbstractArrow`，命中后在上方一格留下 `entity/CarminiteFieldEntity`

`entity/CarminiteFieldEntity` 是普通的 `Entity`（不是生物），存活 75 tick，每 20 tick 对半径 4、高 ±1.5 的圆柱内生物结算一次伤害，每 tick 在边缘画一圈 12 个粒子。源 mod 把它做成了一只无 AI 的飞行生物，范围只能用方盒子，这里都不需要

`registry/ModParticles` 注册三种粒子（`particle_carminite_ball` `_trace` `_effect`），客户端 provider 在 `client/CarminiteParticle`

`registry/ModDamageTypes` 的 `carminite_field` 是反应场伤害，条目在 `data/dawnduskterminal/damage_type/`

这些内容从 `twilightextra`（MCreator 生成）迁移而来，原代码里的过程式实现已换成上面这些

装备贴图目前是原版铁、钻石贴图加色阶的占位图，真美术进来后换掉 `textures/item/` 与 `textures/models/armor/` 里对应前缀的文件即可

## 试验物品

序号表在 `registry/ModExperimentItems`：`TAKEN` 里列掉已经占号的（001 是能当合成台用的方块，在 `ModBlocks`；007 与 017 是两件近战伤害 4、攻速 5 的裸武器，共用 `meleeAttributes` 属性表、只挂属性不挂 tool tier（修饰符 ID 按物品 ID 分开，同一属性上两个同 ID 修饰符会互相顶掉，玩家底数是攻击力 1、攻速 4，所以面板 4/5 对应 +3/+1）；008 是 5 点饥饿、5 点饱和度的食物（系数 0.5，`FoodProperties` 存的是饱和度系数，点数 = 系数 × 饥饿 × 2）；020 是 20 点饥饿、20 点饱和度的食物，效果表是吃完那一刻现摇的（`item/Experiment020Item#rollEffects`：0~3 个、互不相同、各 10~300 秒、等级 I，池子里剔掉瞬发那三个、以及非原版里没有药水瓶的技术性效果（判据是扫 `BuiltInRegistries.POTION`：药水瓶显示的效果就取自这张表，原版不套这条，`bad_omen` 这类照旧留着），随机源当参数传进来好测，`FoodProperties#effects` 放不了随机表也不能放）；019 是一丛植物（`block/Experiment019Block` 继承 `BushBlock`，只站在 `#minecraft:dirt` 与耕地上、底下的土没了自己消失；方块模型 `block/experiment_019` 用 `minecraft:block/cross` 加 `"render_type": "minecraft:cutout"`（漏了这行透明像素会被涂成黑底，整丛变成一块黑方块；原版花的 `ItemBlockRenderTypes.setRenderLayer` 1.19 起废弃，模组方块一律写模型的 `render_type`），贴图直接引 `item/experiment_019` 那张，物品图标仍走 `item/generated`，与原版花一致；掉自身走 `loot_table/blocks/experiment_019.json`）；033/034 是两支突刺长矛，行为在 `item/ThrustSpearItem`；037~042 是钻石五件套与一件普通物品；046 是砷铅铁钼反应权杖；051 是拆解台那台机器，方块在 `ModBlocks`、物品在 `ModItems`；104 是食物，在 `ModItems`；114 是能吃能摆的那一块，方块在 `ModBlocks`、物品在 `ModItems`；115 是暮色森林自带的，本模组不注册），剩下的空号按 `experiment_%03d` 补成占位简单物品，贴图统一借 `experiment_temp.png`。创造栏按序号升序排，115 从暮色森林的注册表里取，没装暮色森林就跳过；试验物品栏用 `withTabsBefore(dawnduskterminal:main)` 钉在主栏目后面——NeoForge 的 `CreativeModeTabRegistry` 对没声明 before/after 的 mod 栏目按注册名字母序排，注册先后不进排序，别的模组的栏目会插进本模组两个栏目中间

试验物品 033/034 是同一份 `item/ThrustSpearItem` 的两个注册。`getUseAnimation` 返回 `UseAnim.BOW`：第一人称的拉满过程与第三人称的双臂拉弓都由原版摆，物品模型必须配同一套变换——两张贴图是按弓的朝向画的，所以 `models/item/experiment_033.json`、`experiment_034.json` 照原版 `item/bow` 给了一份 `display`（thirdperson 的 [-80,260,-40] / [-80,-280,40] 与 -1,-2,2.5 的位移，firstperson 沿用 generated 那一套），挂 `item/generated` 不给变换会变成一张平片，挂 `item/handheld` 则会把矛按工具那样拿反。视野不缩：原版压 FOV 的条件在 `AbstractClientPlayer#getFieldOfViewModifier` 里，是 `getUseItem().is(Items.BOW)`，认物品本体，与非弓物品无关。`getUseDuration` 是 72000，按住右键不会自己结束；`onUseTick` 每 10 tick 取一次目标：`entity_interaction_range` 属性范围内、视线两侧各 30 度锥体里、有视线连通、离视线垂距最小的活生物，`hurt` 掉 1 点并放 `entity.player.hurt_sweet_berry_bush`。伤害类型直接用原版的 `minecraft:sweet_berry_bush`：普通伤害（护甲照减），它本来就在 `minecraft:no_knockback` 标签里，所以只有伤害没有击退；死亡信息与原版一致，本模组不另外写条目，物品也不带描述行。无敌帧不做任何手脚，两次伤害之间隔 10 tick，原版那套逻辑自己会算

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

试验物品 004 是照熔炉工作的那台机器，方块继承原版 `AbstractFurnaceBlock`，状态就是熔炉那对 `facing` 与 `lit`，摆放朝向、旋转镜像、比较器、开界面都走原版抽象层；方块实体没有继承 `AbstractFurnaceBlockEntity`：它的 `serverTick` 是静态方法、每锅的时间由私有方法按配方现算，两处都没有留给子类的口子，而「烧食物快一倍」恰恰要改这两处，所以抄了一份三格容器骨架（`DataComponents.FOOD` 的产物按配方时间的一半算，200 → 100），四个数据槽的索引与熔炉完全一致，界面因此能直接复用原版那套。界面是 `inventory/Experiment004Menu` 继承 `AbstractFurnaceMenu` 加 `client/Experiment004Screen` 继承 `AbstractFurnaceScreen`，面板贴图与火苗、箭头两个 sprite 都用原版熔炉的，配方书跟着原版走。菜单类型单独注册 `experiment_004`：原版 `FurnaceMenu` 把 `MenuType.FURNACE` 写死在构造参数里，继承它的话客户端会按熔炉界面开，本模组的类型与屏幕就白注册了；方块坐标不往客户端传，原版熔炉界面本来就拿空容器、内容靠槽位同步。产物格换成自己的 `ResultSlot`：原版 `FurnaceResultSlot` 只在容器是 `AbstractFurnaceBlockEntity` 时才结算经验，这里改成按 `recipesUsed` 结算，取产物与敲方块时撒经验，行为与熔炉一致。不产生粒子：熔炉的烟与火写在 `FurnaceBlock#animateTick` 里，本方块不是那一支，只在 `Experiment004Block#animateTick` 补了一成概率的火响。资源是 `blockstates/experiment_004.json` 八个变体（`facing` × `lit`）加 `models/block/experiment_004[_active].json`（`minecraft:block/orientable`，正面 `experiment_004_forward[_active]`，其余五面同一张 `experiment_004`）、`loot_table/blocks/experiment_004.json`，物品模型指向方块模型；规格在 `gametest/Experiment004Tests`

试验物品 027 是那支画红石点的笔：`item/Experiment027Item` 按住右键不松（`getUseDuration` 72000，`use` 里两侧都 `startUsingItem`），`onUseTick` 每 tick 在服务端发一个粒子。落点由 `Player#pick(blockInteractionRange, 1.0F, false)` 取：射线走方块轮廓，打中方块就用 `BlockHitResult#getLocation()`，再沿命中面法线让出 0.02 格，免得粒子半边陷进方块里；看空或超出 `block_interaction_range` 属性（默认 4.5 格）时 `pick` 是 MISS，什么都不发。粒子类型是 `ModParticles#EXPERIMENT_027`（`SimpleParticleType`），客户端 provider 在 `client/Experiment027Particle`：继承 `TextureSheetParticle`、贴图表 `particles/particle_experiment_027.json` 直接借原版红石粒子那组 `minecraft:generic_4`，颜色按原版 `DustParticle` 的算法乘成红色，`quadSize` 在原版红石的 0.75 基准上再乘 0.5，初速 0、重力 0、`hasPhysics` 关掉，`lifetime` 写死 1200 tick（60 秒）——不用原版 `ParticleTypes.DUST` 就是因为它的寿命与随年龄收缩的尺寸都写死在 `DustParticleBase` 里。规格在 `gametest/Experiment027Tests`：占位号已让出、粒子类型注册过、正对石头时落点落在那个面上、看空与超距都是 null

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

`onSpawnPlacement` 走 `SpawnRules`

`onRightClickItem` 拦截原版桶，禁止舀两种模组流体

## 结构

`structure/StructureExclusions` 是唯一白名单，`StructureExclusionMixin` 排除不在其中的结构

`structure/StructureExclusions` 里的 `UNMAPPED` 与 `LANDMARKS` 记录未放行项与地标，只服务日志

服务端启动时校验这几张表，缺失只记日志

## 已知遗留

`registry/ModEffects` 仍注册 `portal_sickness`，但已无人施加，方向改由 `stability` 决定

`config/DdtConfig` 的 `portal.cooldown_ticks` 与 `portal.entity_debuff` 已无读取方

语言文件里 `death.attack.dawnduskterminal.portal` 与 `message.dawnduskterminal.searching` 已不再使用
