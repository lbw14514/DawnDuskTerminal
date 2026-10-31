# 架构总览

本模组是一个 NeoForge 模组，新增「晨昏线」维度。

该维度内没有昼夜更替，太阳高度由玩家坐标在晨昏线法向上的投影决定。

维度的地形与群系来自原版主世界世界生成的移植版本，其中的方块被替换为本模组自己的调色板。

## 模块职责

`advancement/` 目录包含自定义进度触发器与判定逻辑。

`block/` 目录包含单独的自定义方块。

`client/` 目录包含客户端渲染、天空、染色、屏幕效果与光影检测。

`command/` 目录包含 `/ddt` 指令。

`compat/` 目录包含特征排序兼容处理。

`entity/` 目录包含自定义生物。

`config/` 目录包含配置定义。

`crafting/` 目录包含自定义 Ingredient。

`fluid/` 目录包含自定义流体。

`gametest/` 目录包含游戏内测试。

`item/` 目录包含自定义物品。

`mixin/` 目录包含针对原版的补丁。

`network/` 目录包含网络包。

`portal/` 目录包含传送门的建造、触发、传送与状态管理。

`progression/` 目录包含 Boss 进度追踪。

`registry/` 目录包含全部 `DeferredRegister`。

`server/` 目录包含服务端事件处理。

`structure/` 目录包含结构白名单与摆放表。

`world/` 目录包含群系源、晨昏线、密度包装、自定义 Feature 与方块、流体反应以及状态管理。

## 入口

主类为 `com.dawnduskterminal.DawnDuskTerminal`。

该类的构造函数按顺序注册各类 `DeferredRegister`。

注册内容包括网络负载 `ModNetwork::registerPayloads`。

流体反应在 `FMLCommonSetupEvent` 上通过 `FluidReactions::register` 注册。

服务端事件挂载于同一处，详见下方「事件」一节。

在客户端环境下，构造函数调用 `DdtClient.init`。

此外还注册 COMMON 配置 `DdtConfig.SPEC`。

静态方法 `id(path)` 生成 `dawnduskterminal:` 命名空间的 `ResourceLocation`。

## 生物

`registry/ModEntities` 注册 `tower_ghast`（高塔恶魂）与 `string`（弦），两者的属性通过 `EntityAttributeCreationEvent` 注册。

两种生物的刷怪蛋注册在 `ModItems` 中，即 `tower_ghast_spawn_egg` 与 `string_spawn_egg`，使用 NeoForge 提供的 `DeferredSpawnEggItem`。原版 `SpawnEggItem` 的构造器要求传入一个现成的 `EntityType`，而物品注册先于实体类型注册执行；该子类接收的是 `Supplier`，同时在 `FMLCommonSetupEvent` 中补充发射器行为，并在 `RegisterColorHandlersEvent` 中登记两层染色。客户端因此只需要一个 `minecraft:item/template_spawn_egg` 模型，该模型的贴图与原版刷怪蛋共用，颜色由染色层提供。`gametest/SpawnEggTests` 通过 `SpawnEggItem#byId` 进行反向查询，以断言这张表确实已被填充。

`entity/StringEntity`（弦）的默认形态（滑行型）只在不稳定流体的液面上活动：它每 tick 把脚底固定回液面，位置一旦离开流体就弹回上一个合法点，因此既不会飞出去，也不会沉下去。水平移动由 `StringMoveControl` 驱动，不经过地面寻路：行为层每 8 到 17 tick 给出一个落在液面上的目标点，控制器**每 tick 都朝它推一次**，目标点进入 0.35 格时才回到 `WAIT`。此处不能沿用原版 `MoveControl` 的做法，因为它一进入 `MOVE_TO` 就把自身改回 `WAIT`，并假设行为层每 tick 重新给出目标点（`PathNavigation` 正是这样做的）；沿用的结果是「推一 tick 滑一程」，实测 20 tick 只移动 0.2 格。空中每 tick 的推进量由 `getFlyingSpeed` 从移动速度属性折算成 `速度 × 0.045`：原版在这一点上对非玩家操控的生物返回恒定值 0.02，速度属性完全不参与，导致大小变种与「追击比巡航快」那一档都会失效。折算后小型个体的巡航速度约为 9.2 格每秒，追击速度约为 12.4 格每秒（行走的玩家为 4.3，冲刺为 5.6）。移速随体型变化：大型个体的基准是小型的 1.35 倍（巡航 12.4 格每秒、追击 16.7 格每秒），0.85~1.15 的随机体型偏移也一并相乘，巡航与追击的倍率两档共用同一套系数。该生物全身自发光：渲染层自行配置了一个材质，使用自发光着色器（`RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER`），不挂载光图，保留半透明（流体贴图 alpha 230），写掩码保持 `COLOR_DEPTH_WRITE`；贴图动画由 GeckoLib 的 `AnimatableTexture` 逐帧上传，与选用哪个 RenderType 无关。**不能直接使用原版 `RenderType.entityTranslucentEmissive`**：它的写掩码是 `COLOR_WRITE`，不写入深度；1.21 的绘制顺序为「实体 → 半透明区块层（流体位于这一层）→ 云」，后两者在实体之后按深度测试混合，因此实体若不写入深度缓冲，就会被这两层整片覆盖，看起来像被流体与云遮挡在后面。渲染层的实现位于 `client/StringRenderer` 的 `glowRenderType`。

「弦」的待机音需要持续播放，因此不采用原版那条「每隔一段随机间隔掷一次」的路径：`getAmbientSound` 返回 null，由 `tickAmbientSound` 按音效长度排期。`string_ambient` 的三个变体取自同一段素材且长度一致（413192 采样 / 44100 Hz = 9.37 秒 = 187 tick），当前这一条还剩 8 tick 时开始下一条，两条叠加在一起听不出断点；音高固定为 1.0，因为变调会改变实际时长，排期便无法对上。`gametest/StringEntityTests` 手动执行「控制器写速度 → 原版 air 分支」两个动作，以断言每 tick 都推进、到位即停、推进量随速度属性变化这三件事。

「弦」有三个行为变种（`StringEntity.Kind`，序号写入同步数据 `DATA_KIND` 与存档 `Kind`），三种变种共用模型、贴图与属性，区别只在于运动系统与索敌范围。运动系统由 `applyKind` 按变种整套装配，能够沿用原版实现的即直接沿用：

**滑行型**贴附液面，`StringMoveControl` 只提供水平推进（每 tick 推一次，到达 0.35 格即视为到达），垂直位置由 `keepOnFluidSurface` 固定；该变种只攻击脚边一格存在流体的玩家。

**低空飞行型**整套沿用蜜蜂的实现：`moveControl = new FlyingMoveControl(this, 20, true)`（原版类，蜜蜂使用的正是它）、`FlyingPathNavigation`，追击玩家时使用原版 `MeleeAttackGoal`（该类自行计算三维寻路路径，因此空中与岸上的玩家都能被追到，索敌不判定流体）。没有目标时，`FlyerWanderGoal` 按 `Bee.BeeWanderGoal` 的写法执行：当 `navigation.isDone() && nextInt(10)==0` 时选取一个液面上方两格的盘旋点，并调用 `navigation.moveTo(navigation.createPath(...), 1.0)` 飞过去。目标消失（被击杀或逃离）后由 `ReturnToFluidGoal` 带回液体上空：距离较远时寻路飞回，距离较近时按蜜蜂在巢口悬停的方式每 tick 以 0.35 的速度给一次目标点。它的 `travel` 沿用 `FlyingMob#travel`（恶魂与幻翼的父类）的写法，**整段不扣除重力**；`checkFallDamage` 沿用 `FlyingMob` 写为空实现。**仅依靠 `setNoGravity(true)` 保持漂浮是不够的**：重力标记一旦被读档或其他代码改写，该生物就会自由落体，而且俯冲追击时 `fallDistance` 会照常累计，落地时结算摔伤；传送门流体不属于 `#minecraft:water`，因此进入传送门流体也不会像原版水那样将 `fallDistance` 清零。

**游动型**沿用鱼类的实现：`moveControl = FluidMoveControl` 参照 `AbstractFish.FishMoveControl` 编写，即在眼睛浸入流体时每 tick 补充 0.005 浮力、速度按 0.125 的比重渐变、按目标点补充垂直推力，并且只改变朝向；`travel` 参照 `AbstractFish#travel` 的水下分支编写（以 `moveRelative(0.01)` 提供推力，`move` 执行移动，整体乘以 0.9，没有目标时缓慢下沉）。原版判定中的 `isInWater()`（`#minecraft:water` 标签）被替换为本模组的传送门流体判定，这是唯一的改动；`keepSubmerged` 再把脚的位置限制在「池底往上 0.2」与「液面往下 0.9」之间，以限定活动范围。该变种只攻击浸在流体中的玩家。

三种变种**只主动索敌玩家**（`NearestAttackableTargetGoal<Player>`，并在 `isValidPrey` 中再判定一次类型）。自然生成只产生滑行型（`world/StringSpawner` 每 5 秒扫描列，玩家周围 48 格内以 5 只为上限）；低空飞行型与游动型只由滑行型分裂产生。滑行型每次受到**高于 2 点**的伤害时有 **10%** 概率裂成一对，即一只低空飞行型（抬高 1.5 格悬空）与一只游动型，同时自身执行 `discard`（而不是 `die`：不掉落战利品、不计入击杀、不播放倒地动画），并伴随末影人传送音与 60 颗传送粒子。两种新变种不会二次分裂。

「弦」的刷怪蛋（`item/StringSpawnEggItem`）只能放置在传送门流体旁边：`useOn`（对方块使用）、`use`（对流体源格使用）与 `createDispenseBehavior`（发射器；默认行为在 `DeferredSpawnEggItem` 中是私有的，因此参照重写了一份）这三条发放路径都先经过 `StringEntity.canSpawnAt` 判定，即落点所在的那一格是流体，或其正下方是流体，才允许放置，否则刷怪蛋不被消耗。

`entity/TowerGhastEntity` 由 GeckoLib 驱动，其模型、动画与贴图从 Yes Steve Model 的 Bedrock 文件转换而来，转换与校验位于 `tools/ysm/`。

`client/TowerGhastRenderer` 直接写出三条资源路径，不遵循 GeckoLib 的命名约定。

`entity/GhastEliteEntity`（大恶魂）是暮色恶魂的精英形态：`Ghast` 的子类，碰撞箱取几何里的主立方体 `thehead`（128 单位见方，即 8×8×8 格，眼高 4 格），生命 250、索敌 100 格（普通 `NearestAttackableTargetGoal<Player>`，没有原版恶魂那条 |Δy| ≤ 4 的过滤；挨打之后立刻记仇，来源是谁就盯谁），经验 317——与暮色森林 `UrGhast` 同一个数。自然生成时问 `GhastElitePopulation`，后者按维度存一张每 tick 刷新的快照，加载范围内已经有 1 只就拒绝，因此一个玩家身边最多一只；群系表里只挂在 `old_growth_birch_forest` 的 monster 表上（权重 1、单批 1 只），刷怪蛋与刷怪笼不受这条容量限制。掉落表为砷铅铁 2~4、恶魂之泪 1~3、高塔恶魂皮 2~4、生恶魂肉 2~4，均带抢夺加成。**有索敌期间按持久处理**（`requiresCustomPersistence`）：它从不靠近目标，`noActionTime` 因此永远得不到清零，原版那条「离玩家超过 32 格且 30 秒没动作就掷骰删除」会让它在战斗中半分钟后凭空消失；脱战后索敌在 100 格外被 `TargetGoal` 放掉，回到原版的消失规则，不会永久占着容量名额。**它必须吃碰撞**：`AbstractHurtingProjectile#canHitEntity` 要求目标不是 `noPhysics`，否则火球根本不命中它，拨火球整条失效，而且不吃碰撞就会穿到地面以下；移动一侧改用 `EliteMoveControl`，照暮色森林 `NoClipMoveControl` 每 2~6 tick 沿目标方向推 0.1 的加速度，只去掉整条直线的碰撞预检（8 格见方的箱子在树林里找不到一条无障碍的直线），撞上树干交给实体自己的移动去滑。

射击与拨弹与「恶果」共用同一份实现。`entity/GhastShootGoal` 是原版 `GhastShootFireballGoal` 的骨架（蓄力 20 tick、第 10 tick 起算蓄力、打完冷却 40 tick、64 格内且视线可见才开火，蓄力与喷吐两声音效由 `entity/GhastVoice` 播），出膛点与「一轮打几发」作为两个钩子传入：大恶魂一轮三发，左右各偏 8 度，沿射线拉开 2 格、左右错开 1.2 格，免得三发在出膛时互相命中；它的火球是 `entity/GhastEliteFireball`，`canHitEntity` 排除一切 `AbstractHurtingProjectile`（暮色森林 `UrGhastFireball` 的做法），其余仍是原版火球的表现（6 点伤害、威力 1 的爆炸）。出膛点取 `mouthPosition`：正对时 4.6 格，斜飞时放大到 `(4.0 + 0.5) / 水平分量`——脸的正面是立方体的一个面，斜 45 度时 `view * 4.6` 落在 (3.25, 3.25)，还在 8 格箱子里，球会从头顶的角冒出来。它的火球登记进 `data/minecraft/tags/entity_type/redirectable_projectile.json`：原版 `Player#attack` 只对 `#minecraft:redirectable_projectile` 里的弹体做 `AIM_DEFLECT`，不加这条标签，拿剑砍它只会空挥一下、球照飞。拨火球挂在 `ProjectileImpactEvent` 上，`entity/GhastDeflect` 取消撞击（于是 `onHitEntity` 的 6 点伤害与爆炸都不发生、火球本身还活着），再把球挪到体中心外 7.5 格（8 格箱的半对角线约 6.93 格）并按当前索敌改向；没有索敌就沿来路弹回，那一声是 `ghast_universal_deflect`（模型包里的 `affectionate_scream`）。两张脸的薄片（`annoyed` 有索敌、`atack` 蓄力中，名字是模型包里就这么拼的）由同步数据 `DATA_FACE` 决定，服务端每 tick 把 `isCharging()` 折进去，客户端只读这一位。

常驻的是泪滴：每 10 tick 落一阵，范围照暮色恶魂——自己的碰撞箱向下 16 格、竖直再放大 16 格、水平方向不放大，只有站在能看见天光处的玩家被砸 3 点，伤害类型借用暮色森林的 `twilightforest:ghast_tear`（缺该模组时退回普通生物伤害），伴随 32 颗下落水滴。落进这片泪滴里的迷你恶魂会被吸收：消失，本体回 2 点。吸收**只在这片泪滴里**判定——贴身吸收是暮色恶魂本体的做法，本模组按「泪滴接触到迷你恶魂」的规格来。一次性技能是召唤：第一次拿到索敌时在 8~24 格的环带里放 6 只 `twilightforest:carminite_ghastling`（高度在中心 ±6 格内随机，撞上就把落点往上抬 2 格、最多试 8 次），并把当前索敌共享给它们；此后每 100 tick 把索敌再共享一次给 24 格内还没目标的小鬼。小鬼**不设** `setPersistenceRequired`（暮色森林的 `makeBossMinion` 也不设），战斗结束后玩家走远便自然消失，不会打一次攒一堆。每 20 tick 照看一次 48 格内的小鬼：没索敌的补一次本体的索敌（暮色森林的 `NearestAttackableTargetGoal` 看不见目标时会把它清成 null，所以要反复补），仍然没索敌的就把**移动目标**指到本体身边 6 格处，它们自己飞过去；有索敌的不动它们的移动，交给它们自己的 AI。这里不改速度也不改位置，6 格又在泪滴柱子（半宽 4 格）之外，所以既不会贴着本体也不会被吸掉。

小鬼打不打人由暮色森林自己的 `CarminiteGhastling#shouldAttack` 决定：玩家头顶戴着雕刻南瓜时不打；3.5 格以内只要看得见就打；更远时必须**玩家的准星对着它**（`dot(玩家视线, 指向它的方向) > 1 − 0.025/距离`）且有视线。照原样，侧身路过的玩家不会被追打，看起来就像「待在天上不动」。因此召唤出来时会打上 `persistentData` 标记 `dawnduskterminal:elite_minion`，`mixin/GhastlingShouldAttackMixin` 对带标记的只在 `shouldAttack` 开头放行（仍然要求有视线，免得隔着树开火）；野生的砷铅铁恶魂守卫没有这个标记，仍按原规则挑人。`@Mixin` 直接写暮色森林的类字面量，目标改名时编译期就会报错。

客户端三条资源路径写在 `client/GhastEliteRenderer`；`parallel0` 里的 perlin 通道由 `client/GhastEliteBonePose` 逐帧补回（两条触手与五条腿的常数与源表达式一一对应，链上各段按 0.6 / 0.35 递减），`parallel1` 不播放——它是 YSM 宿主每帧算出的布条变量，GeckoLib 既不会赋值也不跨帧。这段写入必须挂在 `GeoModel.setCustomAnimations` 上，与高塔恶魂的 `client/TowerGhastBonePose` 同一个位置。角度一律经 `client/GeoBoneRotation`（度、X 与 Y 取反、加初始姿势），与 GeckoLib 读关键帧时的处理一致；手写弧度会让触手的偏航与腿的俯仰整体朝反侧摆。补回的通道不靠人工记：`client/YsmRuntimeContract` 读 `assets/dawnduskterminal/ysm_runtime/ghast_elite.json`（由 `tools/ysm/runtime_contract.py` 从源动画与转换后动画的差异生成），首次渲染后核对清单里每条通道是否都有代码写回，漏一条就打一条警告。

## 装备与武器

`registry/ModArmorMaterials` 注册护甲材质：绯色钼、苍穹与砷铅铁为钻石级，天门松为铁级；另有一份 `experiment_armor` 供试验物品 047~050 那一套使用，其护甲值与铁同级（2 / 6 / 5 / 2，合计 15），每件提供 2 点韧性，附魔值为 9，耐久倍率为 15，维修材料为砷铅铁锭、绯红钼锭与䤧锭中的任意一样（`Ingredient.of` 接收候选清单，命中其中一个即成立）。

`registry/ModTiers` 注册工具分级，同样为四份（绯色钼只有护甲，没有工具）。

`registry/ModEquipment` 按套装注册工具与护甲，六套共 54 件，按套装存放在 `Map<String, Set>` 中，而非逐件声明字段。绯色钼的护甲带有套装效果，其物品位于 `ModItems` 中，该套装只把已有的 holder 拼接进来。

`item/ScarletMolybdArmorItem` 只为绯色钼这一套使用，四件全部穿戴时，每件在 `inventoryTick` 中自行续期生命恢复效果。

绯色钼护甲的穿戴外观不使用原版通用护甲层，而使用源模组（twilightextra）的四件专用模型：`client/model/ScarletMolybdenumArmorModel` 把 `Modelcarmhelmett1`、`Modelcarmchestplatet1`、`Modelcarmleggingst1`、`Modelcarmbootst1` 四份 `LayerDefinition` 逐颗方块照搬，注册为四个 `ModelLayerLocation`，再按槽位拼进 `HumanoidModel`——对应部位替换为专用部件（头 / 身 双臂 / 身 双腿 / 双腿），其余部位填入空的 `ModelPart`，并经 `RegisterClientExtensionsEvent` 的 `IClientItemExtensions#getHumanoidArmorModel` 交给原版 `HumanoidArmorLayer`。护甲层每帧先通过 `copyFrom` 把玩家模型的姿势复制到那个 `HumanoidModel` 的同名部位上，因此专用部件的基准位置必须与原版对齐（头、身 0,0,0；臂 ±5,2,0；腿 ±1.9,12,0），源模型的数值本来就是按这套基准编写的。贴图为 `textures/entities/carmarmort1.png`（64×64，一件套一张，不分内外层），由 `ScarletMolybdArmorItem#getArmorTexture` 返回，从而取代由 `ArmorMaterial.Layer` 拼出的 `textures/models/armor/...`；原来那两张通用层贴图已经删除，因为它们的 UV 布局与这套几何不匹配，而此前通用模型看起来错位正是由此造成。这一套也不接受护甲纹饰：锻造台这条路径通过标签拦截（本套不在 `#minecraft:trimmable_armor` 中，纹饰配方的 base 无法匹配），已带有 `TRIM` 组件的物品栈（来自命令、其他模组或旧存档）由 `ScarletMolybdArmorItem#verifyComponentsAfterLoad` 在加载、网络同步与穿戴时移除，因为纹饰层按原版方块贴图排列，叠加在 64×64 UV 的专用几何上只会错位。规格见 `gametest/EquipmentTests`：四件在内外两种层上都返回那张专用图，且该图本身位于 jar 中；以模板、红石与本套部件查询不出任何锻造配方，带纹饰的物品栈经 NBT 往返一次后组件消失。

`item/CarminiteStaffItem` 的近战行为按单手武器计算，右键使用消耗一颗砷铅铁球并发射一颗 `entity/CarminiteBallEntity`。六种附魔加成沿用源模组的换算（力量、火矢、冲击、多重、穿透、快速装填），该物品挂在 `data/minecraft/tags/item/enchantable/` 的 `bow` 与 `crossbow` 两个原版标签上，否则无法附魔。

`entity/CarminiteBallEntity` 继承 `AbstractArrow`，命中后在其上方一格留下 `entity/CarminiteFieldEntity`。

`entity/CarminiteFieldEntity` 是普通的 `Entity`（不是生物），存活 75 tick，每 20 tick 对半径 4、高 ±1.5 的圆柱范围内的生物结算一次伤害，并每 tick 在边缘绘制一圈 12 个粒子。源模组将其实现为一只无 AI 的飞行生物，作用范围只能使用方盒，这里均不需要。

`registry/ModParticles` 注册三种粒子（`particle_carminite_ball`、`_trace`、`_effect`），客户端 provider 位于 `client/CarminiteParticle`。

`registry/ModDamageTypes` 的 `carminite_field` 是反应场伤害，其条目位于 `data/dawnduskterminal/damage_type/`。

这些内容从 `twilightextra`（由 MCreator 生成）迁移而来，原代码中的过程式实现已替换为上述实现。

装备贴图目前是原版铁、钻石贴图叠加色阶得到的占位图；待正式美术资源到位后，替换 `textures/item/` 与 `textures/models/armor/` 中对应前缀的文件即可。

## 试验物品

序号表位于 `registry/ModExperimentItems`，其中 `TAKEN` 列出了已经占用序号的条目：001 是可以当作合成台使用的方块，位于 `ModBlocks`；007 与 017 是两件近战伤害 4、攻速 5 的裸武器，共用 `meleeAttributes` 属性表，只挂载属性而不挂载 tool tier（修饰符 ID 按物品 ID 区分，同一属性上两个同 ID 修饰符会相互覆盖；玩家底数为攻击力 1、攻速 4，因此面板 4/5 对应 +3/+1）；008 是提供 5 点饥饿与 5 点饱和度的食物（系数 0.5，`FoodProperties` 存储的是饱和度系数，点数 = 系数 × 饥饿 × 2）；020 是提供 20 点饥饿与 20 点饱和度的食物，其效果表在使用的那一刻随机生成（`item/Experiment020Item#rollEffects`：0~3 个、互不相同、各持续 10~300 秒、等级 I，候选池中剔除瞬发的三个，以及非原版中不存在药水瓶的技术性效果，判据是扫描 `BuiltInRegistries.POTION`，因为药水瓶显示的效果就取自这张表，原版不套用这条判据，`bad_omen` 这类效果照旧保留；随机源作为参数传入以便测试，`FoodProperties#effects` 无法承载随机表，也不能放置随机表）；019 是一丛植物（`block/Experiment019Block` 继承 `BushBlock`，只可立于 `#minecraft:dirt` 与耕地上，下方的土消失后自身随之消失；其方块模型 `block/experiment_019` 使用 `minecraft:block/cross` 并加 `"render_type": "minecraft:cutout"`，若遗漏该行，透明像素会被涂成黑底，整丛会变成一块黑方块；原版花所用的 `ItemBlockRenderTypes.setRenderLayer` 自 1.19 起废弃，模组方块一律在模型中写 `render_type`；贴图直接引用 `item/experiment_019` 那张贴图，物品图标仍使用 `item/generated`，与原版花一致；掉落自身由 `loot_table/blocks/experiment_019.json` 定义）；032/033 是两支突刺长矛，行为位于 `item/ThrustSpearItem`；037~042 是钻石五件套与一件普通物品；043 是那台喷火器（`item/Experiment043Item`）；066 是那本恶魂陷阱书（`item/Experiment066Item`）；045 是那面盾（`item/Experiment045Item`，盾面位于 `client/Experiment045ShieldRenderer`）；046 是砷铅铁钼反应权杖；047~050 是那套护甲（`item/ExperimentArmorItem`，见下面的「试验物品 047~050」）；051 是拆解台那台机器，方块在 `ModBlocks`、物品在 `ModItems`；061/070/074 是三块不明肉块（`item/UnknownMeatItem`，三块共用同一份规格）；104 是食物，位于 `ModItems`；107 是整块的红丝绒蛋糕（`item/Experiment107Item`，方块仍是七种可疑蛋糕里的 `sus_redvelvet_cake`）；114 是既能食用又能摆放的那一块，方块在 `ModBlocks`、物品在 `ModItems`；115 由暮色森林自带，本模组不注册。剩余的空号按 `experiment_%03d` 补成占位的简单物品，贴图统一借用 `experiment_temp.png`。创造栏按序号升序排列，其中 115 从暮色森林的注册表中获取，未安装暮色森林时跳过；试验物品栏通过 `withTabsBefore(dawnduskterminal:main)` 固定在主栏目之后——NeoForge 的 `CreativeModeTabRegistry` 对未声明 before/after 的模组栏目按注册名字母序排序，注册的先后顺序不参与排序，否则其他模组的栏目会插入本模组两个栏目之间。

试验物品 032/033 是同一份 `item/ThrustSpearItem` 的两个注册。`getUseAnimation` 返回 `UseAnim.BOW`：第一人称的拉满过程与第三人称的双臂拉弓动作都交由原版播放，因此物品模型必须配置同一套变换——两张贴图是按弓的朝向绘制的，所以 `models/item/experiment_032.json`、`experiment_033.json` 参照原版 `item/bow` 给出了一份 `display`（thirdperson 的 [-80,260,-40] / [-80,-280,40] 与 -1,-2,2.5 的位移，firstperson 沿用 generated 那一套）；若挂 `item/generated` 而不提供变换，模型会变成一张平片，若挂 `item/handheld`，则会把矛按工具的方式拿反。视野不缩放：原版压低 FOV 的条件位于 `AbstractClientPlayer#getFieldOfViewModifier` 中，即 `getUseItem().is(Items.BOW)`，它识别的是物品本体，与非弓物品无关。`getUseDuration` 为 72000，按住右键不会自行结束；`onUseTick` 每 10 tick 选取一次目标：`entity_interaction_range` 属性范围内、视线两侧各 30 度锥体中、有视线连通、且与视线的垂直距离最小的活体生物，对其调用 `hurt` 造成 1 点伤害并播放 `entity.player.hurt_sweet_berry_bush`。伤害类型直接使用原版的 `minecraft:sweet_berry_bush`：属于普通伤害（护甲照常减伤），它本身就在 `minecraft:no_knockback` 标签中，因此只有伤害而没有击退；死亡信息与原版一致，本模组不另外编写条目，物品也不带描述行。无敌帧未做任何改动，两次伤害之间相隔 10 tick，由原版既有逻辑自行计算。

试验物品 104 是 `ModItems` 中的一件普通 `Item`，只挂载了 `Item.Properties().stacksTo(1).food(...)`：该物品可以食用两次，第一次使用时 `usingConvertsTo` 把剩余的那一半转换为 `experiment_104_half`（沿用原版蜂蜜瓶转换为玻璃瓶的路径，即在 `Player#eat` 中替换物品栈），第二次使用时消耗那半块。每次提供 10 点营养与 0.6 的饱和度系数（饱食度 10 × 0.6 × 2 = 12.0，相当于两块烤马铃薯），两次合计为 20 与 24.0。半块同样是 `stacksTo(1)` 的食物，没有 `usingConvertsTo`；它不进入试验物品栏（该栏只排列 001~115 的序号），只能通过食用产生。半块的注册必须写在 104 之前，因为 104 的 `usingConvertsTo` 在注册回调中就需要获取半块的物品栈，而 `DeferredRegister` 按插入顺序注册。

试验物品 061、070、074 是三块不明肉块，三块共用 `item/UnknownMeatItem.FOOD` 这一份 `FoodProperties`，只差贴图：2 点饥饿与 0.3 的饱和度系数（即 2 点饥饿、1.2 点饱和度，与生鸡肉、生羊肉逐项相同）。吃下时以三成概率给 30 秒饥饿（照原版生鸡肉那一条），另有一次独立的三成概率给 5 秒中毒（时长照原版毒马铃薯）。两条效果都写在 `FoodProperties#effects()` 里，由原版 `LivingEntity#addEatEffect` 在服务端逐个按 `random.nextFloat() < probability` 判定，因此不另写物品类去摇效果——020 之所以要现摇，是因为那张效果表本身要随机，肉块这张是固定表。效果用 NeoForge 的 `Supplier` 重载登记：原版的 `effect(MobEffectInstance, float)` 在 NeoForge 上已标为废弃，`MobEffectInstance` 到吃下去那一刻才需要存在。描述行是「不明肉块」，键由物品自己的描述 ID 加 `.desc` 拼出，与 066 那套写法一致。

试验物品 114 的方块是暮色森林试验物品 115 方块（`twilightforest:experiment_115`）的完整复制：两者具有同一套状态 `omnomnom` 0~7 与 `regenerate`（名称均未改动），同样的碰撞形状、比较器与红石输出公式，模型以及侧、底、切口三张贴图直接使用 115 的（`twilightforest:block/experiment115/*` 与 `twilightforest:block/blank`），顶面则替换为本模组自己的 `dawnduskterminal:block/experiment114_top`。不同之处在于：摆放后即为满的 8 片（115 的默认态是 `omnomnom=7`，只剩 1 片）；使用 114 点缺口一次补满（115 一次只补一片）；潜行收走只对全满的方块生效（115 在食用到一半时也能收走）；每消耗一片会额外随机播放一声恶魂受伤音效；撒过红石粉的那一版不补片，而改为在收到一次红石信号时随机播放恶魂受伤音效。

`item/Experiment114Item` 是 `BlockItem`，作为食物提供 32 点营养与 0.3 的饱和度系数，相当于 8 份 115（每份 4 与 0.3）。`useOn` 参照 115：对自身那一块使用时把交互让给方块；非潜行状态下执行摆放，摆不下时才转为食用；食用完成时在服务端以随机音高与音量播放 `entity.ghast.hurt`。

红石去重不依赖额外的 `powered` 状态，而依赖一次方块刻：一次信号会引发多轮邻居更新，`neighborChanged` 在发现这一格已经排入方块刻时便不再发声，这样状态集与 115 完全一致。同理，该方块没有 `randomTicks()`：115 的随机刻只用于给 `regenerate` 那一版补片，而本模组那一版改为播放音效，随机刻没有可执行的操作。

资源为 `blockstates/experiment_114.json` 的 16 个变体（`omnomnom` × `regenerate`），外加 16 个 `models/block/experiment_114_<剩几片>_8[_regenerating].json`；模型内容从 115 的模型原样复制，只改动两处：`_regenerating` 那八个的 `parent` 指向本命名空间，八个基础模型的 `top` 指向 `dawnduskterminal:block/experiment114_top`（`_regenerating` 只覆盖 `top_2`，顶面从父模型继承）。

七种可疑蛋糕（`sus_<口味>_cake`，口味为 `bloodorange`、`cheese`、`cherrycheese`、`lamington`、`pavlova`、`pineapple`、`redvelvet`）是 115 方块那一套的第二个衍生物：方块位于 `block/SusCakeBlock`，物品位于 `item/SusCakeItem`，两者都按 `ModBlocks#SUS_CAKE_FLAVORS` 生成，ID 一一对应。**一个物品就是一片**，摆放下来是 `omnomnom=7`（只剩 1 片，与 115 的默认态相同），一块满的蛋糕为 8 片。此处沿用 `omnomnom` 0~7 那套切口状态、四段碰撞形状、比较器公式 `8 - omnomnom`、`noLootTable`/`pushReaction(DESTROY)`/`WOOL`/`strength(0.5)` 那组方块属性，以及 115 的三条交互：拿同一块蛋糕点缺口一次补一片（满片时返回 `FAIL`），潜行点击一次从缺口取回一片（`omnomnom=7` 时整块取走，播放一声 `item.pickup` 并返还一件物品），空手点击食用一片（经由 `useWithoutItem`）。与 115 不同的有两条：第一，一片提供的量写作 `SusCakeBlock.Serving`（点数 = 系数 × 饥饿 × 2），默认的 `DEFAULT_SERVING` 为 6 点饥饿与 4 点饱和度（系数 1/3），血橙与红丝绒那一档为 `EIGHT_SERVING` 的 8 点饥饿与 8 点饱和度（系数 0.5），该表位于 `ModBlocks#SUS_CAKE_SERVINGS`，而 115 是一片 4 与 0.3；第二，**没有 `regenerate` 这个状态，也不需要撒红石粉**——方块带有 `randomTicks()`，`randomTick` 中只要 `omnomnom` 不为 0 就把片数减一，被随机刻抽中一次即补一片，不再进行第二次判定（与 115 撒粉那一版速度相同，默认 `randomTickSpeed=3` 下平均约 68 秒一片），因此该方块没有 `isSignalSource`/`getSignal`，比较器公式中也没有 114 那个 `+7`。资源为每种口味的 `blockstates/sus_<口味>_cake.json` 八个变体，外加八个 `models/block/sus_<口味>_cake_<剩几片>_8.json`：模型从 115 的那八个原样复制，只把顶、侧、底、切口四张贴图替换为 `dawnduskterminal:block/sus_<口味>_cake_{top,side,bottom,inner}`（`top_2` 仍指向暮色森林的 `blank`，与 114 一致），物品模型走 `item/generated`（红丝绒那五张贴图是临时占位图，由血橙那套按红色调生成，正式贴图到位后直接替换文件即可）。七种蛋糕都进入主栏目的食物那一档，没有战利品表；配方为 `data/dawnduskterminal/recipe/sus_<口味>_cake.json` 六条无序配方（红丝绒暂无配方），原料中都包含 `twilightforest:experiment_115`（血橙配试验物品 008，奶酪配牛奶，樱桃奶酪配牛奶与甜浆果，林明顿配可可豆与骨粉，帕夫洛娃配鸡蛋，反转菠萝配仙人掌）。规格见 `gametest/SusCakeTests`：七种一片默认提供 6 点饥饿与 4 点饱和度（血橙与红丝绒为 8 与 8），摆放下来是最后一片，拿物品一次补一片而满片时拒绝，潜行一次取回一片且最后一片时整块取走，食用一片增加 6 点饥饿与 4 点饱和度，随机刻补一片而满片时不变，比较器读取 `8 - omnomnom` 且不发出红石信号，六条无序配方都能在 `RecipeManager` 中按 ID 取到且原料与产出相符。

整块的那一件是试验物品 107（物品 ID `experiment_107`，位于 `item/Experiment107Item`，续 `SusCakeItem`）：方块仍是 `sus_redvelvet_cake`，与一片那件共用状态、补片、取片与空手吃片，差别只有两条——摆下来就是满的 8 片（覆写 `getPlacementState` 把 `BITES_TAKEN` 置 0，而一片那件摆下来是 7），且不带食物组件（方块物品只负责摆放，要吃就照方块那一套一片一片吃）。名字走自己的键 `item.dawnduskterminal.experiment_107`（覆写 `getDescriptionId`）：`BlockItem` 的默认名字取自方块，不换就会和那件一片的重名。`registerBlocks` 被覆写成空实现：`Item.BY_BLOCK` 由 `BlockItem#registerBlocks` 用 `put` 填，一块方块两件物品时后注册的会顶掉先注册的，整块那件晚于七种一片注册，不拦住就会把方块自己那个 `asItem()` 抢走——补片、取片、比较器几处全都指向整块。物品模型直接以 `block/sus_redvelvet_cake_8_8` 为父（该模型挂 `block/block`，自带 GUI 变换），因此图标是整块蛋糕而不是一片；它排在试验物品栏的 107 号，不进主栏目。

115 再生机（`dawnduskterminal:e_115_regenerator`）是一台按 115 自身的补片速度生产 115 的机器，方块位于 `block/E115RegeneratorBlock`，方块实体位于 `block/entity/E115RegeneratorBlockEntity`。只有两个条件同时成立时才进行随机判定：**侧面存在红石信号**，且**顶上是一块完整并撒过红石粉的 115**（`omnomnom=0` 且 `regenerate=true`）；产物格已满时停止工作。顶面被刻意排除在「侧面」之外：满片且撒过粉的 115 自身就是信号源，`getSignal` 恒返回 `15 - omnomnom × 2`，并向四个方向发出信号，因此当它压在机器上时 `Level#hasNeighborSignal` 永远为真，判定只能逐面查询四个水平方向的 `Level#getSignal(邻居, 朝我)`（红石粉、红石块、正对着它的中继器与比较器、贴在侧面的拉杆都计算在内，顶面与底面的读数一概不看）。顶上那一块不会被消耗，也不需要它处于「食用到一半、正在补片」的状态：满片加撒粉即为触发条件，随机判定命中的那一次产出的是**新的** 115 物品。状态名按字符串查找（`omnomnom`、`regenerate`）：那两个是暮色森林方块的状态属性名，没有可引用的编译期常量，方块本身按注册项引用。速度按随机刻的机制推算，而不凭主观估计确定数值：原版随机刻对每个 16³ 区块段每 tick 抽取 `randomTickSpeed`（默认 3）个方块位置，任意一格每 tick 有 3/4096 的概率被抽中，而 115 在 `regenerate=true` 且未满片时**被抽中一次即补一片，不再进行第二次判定**；因此方块实体每 tick 执行一次 `random.nextInt(4096) < 3` 的判定，命中即产出一份，结果随机且不可预期，期望间隔为 4096/3 ≈ 1365.3 tick（20 tps 下约 68.3 秒），与 115 自身补片完全等价。该机器没有进度条，也没有可保存的进度，格子里是否存在物品就是全部状态。每次产出播放一声顶上那块 115 的放置音效（115 使用羊毛音效，即 `block.wool.place`，与暮色森林给 115 补片时那一声同源）；工作时 `animateTick` 会在顶上那一格中随机位置生成一颗 `particle_carminite_ember`，每次 `animateTick` 生成一颗且不叠加概率，因为客户端的 `animateTick` 本来就不是每 tick 都轮到某个方块，单个方块平均一秒只被选中两三次。玩家手持 115 点击过来时，方块不抢占交互（`useItemOn` 返回 `SKIP_DEFAULT_BLOCK_INTERACTION`）：原版的执行顺序本就是「物品的 `useOn` 先执行，摆放成功即结束」，因此顶面照常摆放物品到方块上方，走到方块这一层只剩下「摆放不下」的情形，此时也不会同时打开界面；只有空手点击才打开界面。容器只有一格产物格，漏斗**只从底面抽取**（`getSlotsForFace` 对其余五个面返回空表），任何面都无法放入物品（`canPlaceItem` 恒为 `false`），比较器按填充度输出。界面为 `inventory/E115RegeneratorMenu` 与 `client/E115RegeneratorScreen`：面板使用本模组那张 `textures/gui/e_115_regenerator.png`（256x256 画布，176x166 的面板绘制在左上角），不注册 sprite，也不在屏幕中另行绘制其他内容；格子坐标全部参照原版 `DispenserMenu`——机器那一格取原版 3x3 的正中 (80, 35)，背包从 (8, 84) 起，快捷栏从 (8, 142) 起，面板上的凹槽即按这几个数值绘制；打开界面通过 `IMenuProviderExtension#writeClientSideData` 传入坐标，并通过 `IMenuTypeExtension.create` 创建类型，与 051 使用同一套做法。资源为 `blockstates/e_115_regenerator.json` 一个变体（该方块没有状态），外加 `models/block/e_115_regenerator.json`（`cube_bottom_top`，含顶、侧、底三张贴图）与 `loot_table/blocks/e_115_regenerator.json`，物品模型指向方块模型，方块进入 `#minecraft:mineable/pickaxe`，并在创造栏中排在主栏目。规格见 `gametest/E115RegeneratorTests`：摆好机器后直接调用 `serverTick`，断言随机判定的分母与每 tick 抽取次数均与随机刻那一档一致、条件满足时运行足够长的时间必然产出且产出的是 115 物品、顶面 115 自身发出的信号不计作侧面信号（运行足够长也不产出）、顶上的条件缺少任意一条都不成立（食用到一半 / 未撒粉 / 空着）、产物格已满时不再放入、漏斗只从底面抽取且任何面都无法放入、手持 115 时返回 `SKIP_DEFAULT_BLOCK_INTERACTION` 而其他物品走默认流程、产出音效即羊毛放置音效。

试验物品 051 是拆解台那台机器，其方块属性参照暮色森林 `twilightforest:uncrafting_table` 的注册参数（`ignitedByLava`、`mapColor(FIRE)`、`sound(WOOD)`、`strength(2.5)`，同时也一并进入 `#minecraft:mineable/axe`）；状态只有一个 `active`：有燃料燃烧时切换为 `_active` 那套贴图，`Properties.lightLevel` 据此提供 13 级光照（数值与原版熔炉的 `litBlockEmission(13)` 相同；原版那个辅助方法写死了 `BlockStateProperties.LIT`，因此只能自行编写一行）；`animateTick` 参照 `FurnaceBlock` 的音效部分以 10% 概率播放 `block.furnace.fire_crackle`——烟与火粒子未被沿用，因为原版是从 `FACING` 那一面喷出的，而本方块四面对称且没有朝向状态。与拆解台的功能差别（面板只有拆解、不收取经验、不更换配方、燃烧燃料、直接产出）写在 `block/entity/Experiment051BlockEntity` 的类注释中；机器刻的骨架参照原版熔炉的 `AbstractFurnaceBlockEntity#serverTick`，只是把「是否有活可干」从一格结果改为 3x3 出料格（`canStore`），任何一格放不下即停工，并将进度清零。

产物与拆解台保持同源依靠三样东西：`crafting/UncraftingResolver` 按 `UncraftingMenu#getRecipesFor(ItemStack, Level)` 的顺序取第 0 条（先反查合成配方，再查拆解配方，最后才是本方块自行添加的熔炼回退），并按拆解台的宽高将布局铺到 3x3；`compat/TwilightUncrafting` 直接引用暮色森林的 `TFRecipes.UNCRAFTING_RECIPE`、`UncraftingRecipe#isItemStackAnIngredient` / `#getCount` 与 `TFConfig` 里那一组白名单及黑名单字段——类型绑定在编译期，暮色森林改名时构建直接失败。配方扫描的开销高于熔炉的 `CachedCheck`，因此只在原料更换、数量增加、不足以满足当前配方、或 `RecipeManager` 实例更换（数据包重载）时重新扫描。

只有拆解配方而没有合成配方的物品（例如试验物品 017：四个钢叶加一片黑树树叶，见 `data/dawnduskterminal/recipe/experiment_017_uncrafting.json`）按暮色森林的 `twilightforest:uncrafting` 格式编写：`input` 是可以进入机器的那件物品，`input_count` 是消耗的数量（省略时为 1），`key`/`pattern` 就是 3x3 产物网格，空格子不产出物品；`cost` 是暮色森林那张拆解台自身收取的经验等级，本方块不扣除等级，因此写 0 即可。这类配方需要读取暮色森林的类型，未安装暮色森林时会在配方加载阶段报告一条解析错误——暮色森林是硬依赖，因此这一行为可以接受。

界面为 `inventory/Experiment051Menu` 与 `client/Experiment051Screen`，面板为 186x166（`textures/gui/experiment_051.png`，比原版熔炉宽一圈才足以容纳 3x3）；火焰与箭头不自行绘制，而是直接 `blitSprite` 原版熔炉的 `container/furnace/lit_progress` 与 `container/furnace/burn_progress`，因此面板贴图中那两个空框的位置必须与菜单的 `FLAME_`/`ARROW_` 常量对齐。坐标通过 `IMenuProviderExtension#writeClientSideData` 传递（原版的开包不携带坐标），菜单类型使用 `IMenuTypeExtension.create`。漏斗的顶面输入原料，侧面输入燃料，底面抽取产物，合法性判定统一由方块实体的 `canPlaceItem` 处理，因此界面与漏斗的行为不会出现分歧。

资源为 `blockstates/experiment_051.json` 两个变体，外加 `models/block/experiment_051[_active].json`（`cube_bottom_top`，顶、侧、底三张贴图各有 `_active` 版本）与 `loot_table/blocks/experiment_051.json`，物品模型指向方块模型。规格见 `gametest/Experiment051Tests`：机器刻通过直接调用 `serverTick` 运行完毕，不依赖 GameTest 等待方块实体自然刻。

试验物品 052 是参照砷铅铁反应堆制作的那颗方雷，方块位于 `block/Experiment052Block`，状态只有一个 `active`：收到**任意一个**红石信号（`Level#hasNeighborSignal`，红石粉、红石块、贴面拉杆、中继器都计算在内）即切换为 `active`，模型更换为 `experiment_052_glow`，`Properties.lightLevel` 提供 15 级光照，播放一声 `experiment_052_ignite`，再通过 `scheduleTick` 排入 `FUSE_TICKS` = 60 tick（3 秒）的引信；`tick` 到达该时刻即爆炸，点燃之后即使断电也无法停止（没有取消分支）。触发只承认「从未点燃变为点燃」这一次状态变化，`onPlace` 也会执行一次同样的判断，因此摆放在已经通电的位置上会立即点火。爆炸本体位于 `world/CarminiteBlast#large`：它调用 `ServerLevel#explode` 那个携带粒子与音效参数的十二参重载，粒子参数两档都填入 `particle_carminite_blast`，音效填入 `experiment_052_explode`——该重载把两者写入 `ClientboundExplodePacket` 一并发送给客户端，服务端不再生成任何原版 `explosion`/`explosion_emitter` 粒子；随后本模组自行生成两层粒子：先是一圈 48 颗的逆时针旋臂（`CarminiteBlast#ring`：按角度铺开，越靠外圈转过的角度越大，水平分量取 `(cos θ, -sin θ)`，速度由 0.45 增至 1.35，并带竖向抖动），再生成一遍 `particle_carminite_blast`——它与反应场那种 `particle_carminite_effect` 使用**同一批八帧贴图**，只换用一份帧序更慢的配置（`CarminiteParticle#blastProvider`，帧间隔 3 tick、寿命 20 tick，反应场那种保持原样），分布按原版爆炸的规律确定：**落在球形范围内，半径等于威力本身**（不乘系数，威力 6 对应 6 格、威力 2 对应 2 格），颗数随威力增加（威力 × 18，限定在 24~160 之间），每颗沿半径向外带有**较小**的初速（0.05~0.20）；由于位置与初速都必须逐颗计算，因此采用 `sendParticles` 的 `count == 0` 那条路径。之后补充一颗烟花白色闪光粒子 `minecraft:flash`，并按威力在附近地面补火（与原版一致，随威力变化：威力 × 3 团，最少 3 团；撒开半径等于威力，向下查找 威力/3 格，威力 6 时为 18 团），同时甩出 0~15 个碎片。爆炸范围内的同类方块会发生连锁，但一次最多五个：`CarminiteBlast#protectArmedBlocks` 挂在 `ExplosionEvent.Detonate` 上，主爆炸进行中先把它从 `getAffectedBlocks()`（即 `Explosion#getToBlow()`）中**所有** `experiment_052` 移除——这种方块的爆炸不破坏同类方块，范围内未被连锁到的那些便保持原样；再从移除的位置中挑选距离爆心最近的五个，直接点亮其状态并排入一个 **1~10 tick 的随机**计划刻——跳过点火（不播放音效，也不等待 3 秒引信），到点后自行爆炸。每个被引爆的方块都会重复同样的流程，因此连锁最多五路，每跳随机 1~10 tick，多出来的同类方块会被后续的连锁卷入。此外，每次爆炸（主爆炸与碎片的小爆炸都计入）都会把附近区域刷成 `dawnduskterminal:old_growth_birch_forest`：它调用原版 `/fillbiome` 的那条实现 `FillBiomeCommand#fill`（按 4×4×4 单元修改数据，并向附近玩家发包同步），半径等于威力 × 2 格（最少 4 格），调用前先把半径覆盖的区块载入，修改失败则跳过，不抛出异常。资源为 `blockstates/experiment_052.json` 两个变体，外加 `models/block/experiment_052[_glow].json`（`cube_all`）与 `loot_table/blocks/experiment_052.json`，物品模型指向方块模型。

砷铅铁火焰是 `block/CarminiteFireBlock`，它继承原版的 `BaseFireBlock` 而不是 `FireBlock`：蔓延、烧毁方块以及按火消耗可燃物等行为全部位于 `FireBlock#tick` 中，而 `BaseFireBlock` 只保留「火这个方块」应有的内容，`canBurn` 再被固定为 `false`。`entityInside` 被整体替换（原版那一段会给予 8 秒着火状态），只保留接触时造成 2 点伤害，触发频率交由生物的受伤无敌时间控制，因此原地站立时为每 0.5 秒 2 点；伤害类型是本模组的 `carminite_fire`，它**只进入 `#minecraft:no_knockback`，不进入 `#minecraft:is_fire`**——若进入 `is_fire`，就会被 `Entity#isInvulnerableTo` 中的火焰免疫拦下，而砷铅铁火焰需要能够烧穿烈焰人这类生物；`effects: burning` 只替换受击音效，不会点燃目标。熄灭行为**沿用原版火焰**（即 `FireBlock#tick` 去掉蔓延）：每 30~39 tick 排入一个计划刻，淋雨时按 `0.2 + age × 0.03` 的概率熄灭，`AGE` 每次增长 0~1，未落在火源上且无法引燃相邻可燃方块时，`age > 3` 即熄灭——也就是压在石头上的原版火焰的寿命；支撑方块消失后立即掉落，`onPlace` 不调用 `super`（否则会在主世界与下界顺带点出下界门）。模型采用**原版火焰那一套**：状态包含 `north`/`east`/`south`/`west`/`up` 五个朝向位以及 `age`，方块状态为与 `minecraft:fire` 同构的六个 multipart 段，十张模型 `carminite_fire_{floor,side,side_alt,up,up_alt}[01]` 各自继承对应的 `template_fire_*`，只替换 `fire` 那个贴图变量，`render_type` 写作 `minecraft:cutout`；某一面是否需要绘制板子由邻居的实心面决定（`isFaceSturdy`），因此贴着墙面或顶着天花板时会铺满整格。环境音与烟均未沿用原版，改为在 `animateTick` 中每次生成三颗砷铅铁余烬 `particle_carminite_ember`（客户端的 `animateTick` 一秒才轮到单个方块两三次，若再叠加概率便无法看见；原版火焰的可见效果同样依靠每次三颗烟粒子维持）。余烬是另行增加的粒子类型：贴图使用原版红石粒子那组 `minecraft:generic_0..7`，尺寸为原版红石粒子的 1.5 倍（全程不变，没有随年龄增大的机制），始终满亮，八张贴图按年龄播放为序列帧，并具有负重力向上抬升与横向阻尼（`client/CarminiteEmberParticle`）；其初速由自身编写，不经过 `Particle` 那个会附加 ±0.4 随机速度的构造器。碎片拖出的红线也使用同一种粒子。方块属性参照原版火焰（`replaceable`、`noCollission`、`instabreak`、`WOOL`、`DESTROY`），光照 10 级，带 `noLootTable`，没有物品形态，只能由 052 的爆炸与碎片生成。

碎片是 `entity/CarminiteShrapnelEntity`，它继承 `ThrowableProjectile`：重力（0.03）与空气阻力（每 tick ×0.99）由父类提供，本类只剩两支回调。客户端注册的是 `NoopRenderer`，外观仅表现为每 tick 一颗 `particle_carminite_ember`；命中生物时造成 1~10 点随机伤害，使用 `damageSources().explosion(this, 发射者)`（原版爆炸伤害），弹体不消失，同一只生物只承受一次；命中生物与命中方块都会在命中点引发一次小型爆炸（`CarminiteBlast#small`：威力 2，`ExplosionInteraction.NONE` 不破坏方块，使用一个将 `shouldDamageEntity` 与击退都关闭的 `ExplosionDamageCalculator`，音效为原版那声 `entity.generic.explode`，粒子同一套，另加一颗白色闪光粒子），并补充几团 `carminite_fire`；命中方块之后弹体消失，若 100 tick 内尚未撞到任何对象也会自行消失。规格见 `gametest/Experiment052Tests`：占位号已让出、物品是那个方块的 `BlockItem`、15 级光照、一个信号即点燃、引信运行满 3 秒后方块消失、火焰造成 2 点伤害且不点燃、火焰按原版年龄机制自行烧尽、碎片造成的是爆炸伤害、碎片落地补火、方块状态与十张模型与原版火焰一致。

试验物品 065 是反建器那一档中更高级的一台（这一件同样是本模组自己的设计），方块位于 `block/Experiment065Block`：它没有状态，也没有 ticker，物品是它的 `BlockItem`，方块属性参照 052 那一档的塔木机器（3.5 硬度、石头音效、`COLOR_ORANGE`、进入 `#minecraft:mineable/pickaxe`），`Properties.lightLevel` 常亮 15 级。它压制的作用场为 17×17×17（`world/Experiment065Field` 的 `RADIUS` 8），判定只有一条：**伤害由谁打出**——取 `DamageSource#getDirectEntity()`（不存在时才退回到 `getEntity()`），若该实体位于场内，则取消这一次伤害。近战中的「直接实体」是攻击者本人，射弹中是那颗箭或火球，爆炸中是 tnt 那颗实体，因此从场内射出的箭飞出场后照常造成伤害，而场外射入的箭在飞到目标身上时已位于场内，反而无法造成伤害；这一条是「攻击源自范围内」的直接后果，规格中专门为此设置了一条断言。环境伤害（摔落、火焰、虚空、仙人掌、饥饿）的来源不存在实体，场不做任何判定。取消挂在 `LivingIncomingDamageEvent` 上：它在 `LivingEntity#hurt` 中位于无敌帧判定之后、一切减伤之前，取消的效果等同于这一次伤害从未发生——没有伤害、没有击退、没有受击音、不消耗护盾耐久，也不进入无敌帧；客户端那份 `hurt` 也会发出该事件，因此先拦截非服务端的情况。场的中心不依靠每 tick 扫描 17³ = 4913 格：`world/Experiment065Field` 按维度存储一张 `Set<BlockPos>`，方块实体的 `onLoad` / `setRemoved` 负责区块载入与卸载，方块的 `onPlace` / `onRemove` 负责摆放与挖掘（因此摆放的那一 tick 即生效，不必等待下一 tick 的方块实体装载），伤害事件只扫描现存的场中心；每次判定还会回头核对那一格是否仍是本方块，即使方块实体那边的撤登记确有遗漏，也不会留下一个无法造成伤害的幽灵场；查询之前先调用 `ChunkSource#hasChunk`（`LevelReader` 那几个 `hasChunkAt` 在 1.21.1 中已废弃），不会因此将区块读入。方块实体没有 `ticker`，因为场按位置查询，没有每 tick 必须执行的操作。贴图只有 `experiment_065` 一张：方块自身发出 15 级光照，贴图直接读取为满亮，不再叠加发光层（原先那套向外膨胀 0.02 的双层 cutout 模型已经拆除，`experiment_065_glow.png` 目前没有任何引用）。场的范围由 `world/CubeFieldMarker` 绘制，整段参照暮色森林反建器（`twilightforest:antibuilder` 的 `AntibuilderBlockEntity#tick` / `makeOutline` / `drawParticleLine`，对照 4.8.3345 源码）编写：使用红石粉（`DustParticleOptions.REDSTONE`）绘制框线，每 tick 在方块自身那一格中飘浮一颗粉尘，每 10 次选取一次并连续绘制三组框线；每组由两段构成——一条斜线（中心到某个角，或起始角到中心，随机二选一）加上该角所在的那条棱，十二条棱分为顶面四条、底面四条与竖直四条；框的端点为 `pos ± RADIUS` 与 `pos + RADIUS + 1`，即方块中心往外各 8.5 格（`CubeFieldMarker#halfExtent`），恰好压在场最外层那几格的外侧面上。与该实现的差别只有三处：半径（那边为 4，这里为 8）、每条线的粒子数（那边一段固定 16 颗、棱长 5 格；这里按长度每格 3 颗，密度一致），以及场内究竟发生什么。粒子只在客户端绘制，并且只在 16 格内存在存活玩家时绘制（那边同样使用 `hasNearbyAlivePlayer(..., 16)`）：两个方块都把 `EntityBlock#getTicker` 覆写为「客户端取得 `CubeFieldMarker::tick`，服务端返回 `null`」，服务端不发送任何粒子，也不使用 `sendParticles`。资源为 `blockstates/experiment_065.json` 一个变体（没有状态），外加 `models/block/experiment_065.json`（即 `cube_all`）与 `loot_table/blocks/experiment_065.json`，物品模型指向方块模型（占位物品原来那张 `minecraft:item/generated` 加 `experiment_temp` 的图标也一并替换）。规格见 `gametest/Experiment065Tests`：占位号已让出、物品是那个方块的 `BlockItem`、方块没有状态且常亮 15 级光照、进入 `#minecraft:mineable/pickaxe`、场为 17 格（三条轴各在 8 格处无法造成伤害、9 格处可以造成伤害）、场内攻击场内与攻击场外都无法造成伤害、场外攻击进来照常生效、环境伤害不受拦截、射弹按弹体位置计算、摆放当 tick 生效而挖掘后即停止、模型为单层 `cube_all` 且只有一张贴图、服务端没有 ticker。

试验物品 069 是反建器那一档中更高级的第二台，与 065 共用 `world/CubeField` 那张 17×17×17 的场中心表（按维度存储 `Set<BlockPos>`，方块实体 `onLoad`/`setRemoved` 负责区块载入与卸载，方块 `onPlace`/`onRemove` 负责摆放与挖掘，判定时回头核对那一格是否仍是本方块），只是场内发生的事有所不同：**落在场内的方块更新不被处理**。其概念取自原版更新抑制器——更新链把 `CollectingNeighborUpdater` 的计数顶到上限之后，之后排入队列的更新会被整批丢弃；这台机器把「一段时间」替换为「一个固定范围」。判定只有一条 `Experiment069Field#suppressed(level, pos)`，且**考察被更新的那一格**：更新总是「某一格收到了一个更新」，只有收到更新的那一格位于场内时才被丢弃，因此场内的变化照常会把更新发送给场外的邻居。四个入口由三个 mixin 引入：`mixin/BlockStateUpdateSuppressMixin` 拦截 `BlockStateBase#handleNeighborChanged`（邻居更新唯一的投递口，`NeighborUpdater.executeUpdate` 由 `CollectingNeighborUpdater` 的三种 `NeighborUpdates` 调用的正是它）、`#tick`（方块计划刻，`ServerLevel#tickBlock` 到点调用它）与 `#randomTick`（随机刻，`ServerLevel#tickChunk` 选中后调用它）；`mixin/LevelShapeUpdateSuppressMixin` 拦截 `Level#neighborShapeChanged`（形状更新的排队口，`BlockStateBase#updateNeighbourShapes` 按六个方向逐个调用它；此处拦截而不拦截 `BlockStateBase#updateShape`，因为后者还会被结构模板落地与区块升级数据直接调用，若拦截它，那两条路径也会一并受到影响）；`mixin/FluidStateUpdateSuppressMixin` 拦截 `FluidState#tick` 与 `#randomTick`（水与岩浆的流动经由 `ServerLevel#tickFluid` 那条流体队列处理，与方块刻不是同一条路径）。在**执行口**而非排队口拦截，正是更新抑制器本来的设计：更新照常排入队列，排到场内时被丢弃。没有场的地方每条只消耗一次 `HashMap` 查表——邻居更新与随机刻都是每秒上千次的热路径，此处不能引入开销更高的操作。方块属性与 065 同档，地图色使用 `TERRACOTTA_ORANGE`，与 065 的 `COLOR_ORANGE` 区分；资源为 `blockstates/experiment_069.json` 一个变体，外加 `models/block/experiment_069.json`（与 065 一样即 `cube_all`，单层一张贴图）与 `loot_table/blocks/experiment_069.json`，物品模型指向方块模型；场的大小同样由 `world/CubeFieldMarker` 那套红石粉框线绘制（与 065 同一份实现，在客户端绘制，16 格内存在玩家时才绘制，服务端不挂载 ticker）。规格见 `gametest/Experiment069Tests`：占位号已让出、物品是那个方块的 `BlockItem`、方块没有状态且常亮 15 级光照、进入 `#minecraft:mineable/pickaxe`、场为 17 格，邻居更新（红石灯通电后不亮）、形状更新（栅栏不连接）、计划刻（沙子不坠落）、随机刻（冰不融化）、流体刻（水不流动）各设置一条断言，且各带场外对照，摆放当 tick 生效而挖掘后即停止，模型为单层 `cube_all`、立方框的八个角位于中心外各 8.5 格、服务端没有 ticker；这些测试**一条一个 batch**——场为 17 格，而测试结构只有 16 格，一台机器压制的场必然伸到自身结构之外，而同一个 batch 内的测试是并行执行的（阵位只相隔 5 格），挤在一起会互相压制更新，此前 052 的连锁测试正是因此被压制为失败。

试验物品 004 是参照熔炉工作的那台机器，方块继承原版 `AbstractFurnaceBlock`，状态就是熔炉那对 `facing` 与 `lit`，摆放朝向、旋转镜像、比较器与打开界面都走原版抽象层；方块实体没有继承 `AbstractFurnaceBlockEntity`：它的 `serverTick` 是静态方法，每一锅的时间由私有方法按配方即时计算，这两处都没有留给子类的扩展点，而「烧食物快一倍」恰恰需要改动这两处，因此复制了一份三格容器骨架（`DataComponents.FOOD` 的产物按配方时间的一半计算，200 → 100），四个数据槽的索引与熔炉完全一致，界面因此可以直接复用原版那一套。界面为 `inventory/Experiment004Menu` 继承 `AbstractFurnaceMenu`，加上 `client/Experiment004Screen` 继承 `AbstractFurnaceScreen`，面板贴图以及火苗、箭头两个 sprite 都使用原版熔炉的，配方书跟随原版。菜单类型单独注册 `experiment_004`：原版 `FurnaceMenu` 把 `MenuType.FURNACE` 写死在构造参数中，若继承它，客户端会按熔炉界面打开，本模组注册的类型与屏幕则不会生效；方块坐标不向客户端传递，因为原版熔炉界面本来就获取空容器，内容依靠槽位同步。产物格替换为自己的 `ResultSlot`：原版 `FurnaceResultSlot` 只在容器是 `AbstractFurnaceBlockEntity` 时才结算经验，这里改为按 `recipesUsed` 结算，在取出产物与敲碎方块时释放经验，行为与熔炉一致。该方块不产生粒子：熔炉的烟与火写在 `FurnaceBlock#animateTick` 中，本方块不属于那一支，只在 `Experiment004Block#animateTick` 中补充了一成概率的火响。资源为 `blockstates/experiment_004.json` 八个变体（`facing` × `lit`），外加 `models/block/experiment_004[_active].json`（`minecraft:block/orientable`，正面为 `experiment_004_forward[_active]`，其余五面同一张 `experiment_004`）与 `loot_table/blocks/experiment_004.json`，物品模型指向方块模型；规格见 `gametest/Experiment004Tests`。

试验物品 027 是那支绘制红石点的笔：`item/Experiment027Item` 在按住右键不放时工作（`getUseDuration` 为 72000，`use` 中两侧都调用 `startUsingItem`），`onUseTick` 每 tick 在服务端生成一个粒子。落点由 `Player#pick(blockInteractionRange, 1.0F, false)` 获取：射线沿方块轮廓进行，命中方块时使用 `BlockHitResult#getLocation()`，再沿命中面法线让出 0.02 格，以免粒子有一半陷入方块内部；若看向空处或超出 `block_interaction_range` 属性（默认 4.5 格），`pick` 返回 MISS，则不生成任何粒子。粒子类型为 `ModParticles#EXPERIMENT_027`（`SimpleParticleType`），客户端 provider 位于 `client/Experiment027Particle`：它继承 `TextureSheetParticle`，贴图表 `particles/particle_experiment_027.json` 直接借用原版红石粒子那组 `minecraft:generic_4`，颜色按原版 `DustParticle` 的算法相乘为红色，`quadSize` 在原版红石 0.75 的基准上再乘 0.5，初速为 0，重力为 0，关闭 `hasPhysics`，`lifetime` 固定为 1200 tick（60 秒）——不使用原版 `ParticleTypes.DUST` 的原因在于它的寿命与随年龄收缩的尺寸都写死在 `DustParticleBase` 中。规格见 `gametest/Experiment027Tests`：占位号已让出、粒子类型已注册、正对石头时落点位于那个面上、看向空处与超出距离时均为 null。

试验物品 043 是那台手持喷火器：单击右键即可把暮色森林封装的喷火器（`twilightforest:encased_fire_jet`）从接收红石信号到冷却结束的整套过程沿视线释放，逐拍对齐的实现写在 `item/FireJetProcess` 中。方块的实现位于 `EncasedFireJetBlock#neighborChanged`：它从 IDLE 切换到 POPPING 并播放 jet_start（`block.comparator.click`，音量 0.3、音高 0.6），`FireJetBlockEntity#tickPopping` 在计数 20/40/60 时各生成 8 颗 `LAVA` 并播放一声 `block.lava.pop`，到 80 时切换为 FLAME；`tickFlame` 运行 61 拍（计数 1..61，第 61 拍溢出归零后仍处理一拍）：第 1 拍再次播放 jet_start，每 2 tick 生成一颗大烟加五路大火焰（中心一路，上下左右各偏移 1 格，初速沿轴 0.75，两侧各带 0.05 的向心分量），每 4 tick 播放一声 jet_active（`entity.ghast.shoot`），每 5 tick 对伤害立方体内的活体生物造成 2 点伤害并点燃 300 tick，最后切换到 TIMEOUT。此处整段沿用该实现，只把「方块朝上」替换为「玩家的视线」：枪口为眼睛前方 1 格，右轴与上轴由视线计算得出，四路火苗按这对轴摆放，伤害立方体也随之旋转（沿轴 14 格、半径 2 格）；**枪口与朝向每 tick 重新绑到玩家身上**（`refresh`），因此走开与转身时火焰随之移动与旋转，玩家下线、死亡或更换维度时则中断；施放期间若把用于点火的那只手换成其他物品，剩下的火焰改为从玩家**脚下**朝正上方喷射，并连同玩家自身一起烧灼（那一档不再把射手排除在伤害判定之外），换回 043 后即恢复按视线喷射。伤害提升至 4 点（方块为 2 点），点燃仍为 300 tick，火焰免疫会被跳过，也不判断遮挡（与方块相同），射出者是玩家；施放者与它的同族不会被烧到（`entity/GhastKin`，见 066 那一段），同一个施放者也不会叠出第二道火焰；伤害类型进入 `#minecraft:bypasses_cooldown`，因此每一拍都能实际打出，不会被受击后的无敌时间阻挡。过程挂在 `ServerTickEvent.Post`（`DdtServerEvents`）上推进，因此单击一次即可，松手与更换物品都不会中断，也不使用 `startUsingItem`。大火焰粒子由 `particle/JetFlameParticleOptions`、`JetFlameParticleType` 与客户端 `client/JetFlameParticle` 组成：尺寸为原版火焰粒子的五倍，随寿命收缩到一半，寿命为 12~44 tick，速度逐 tick 衰减 0.96——原版粒子包只能由客户端即时随机生成速度，因此初速被放入粒子选项一并发送。物品耐久为 64 点，每次喷射扣除 1 点；冷却在启动那一刻挂载 `TOTAL_TICKS + 60` = 201 tick（等价于「运行结束后再冷却 3 秒」）。伤害类型 `dawnduskterminal:experiment_043`（`data/dawnduskterminal/damage_type/experiment_043.json`，`effects: burning`）与暮色森林的 `fire_jet` 一样进入 `#minecraft:no_knockback`、`#minecraft:is_fire` 与 `#minecraft:ignites_armor_stands`，并另外加入 `#minecraft:bypasses_cooldown`，死亡信息为 `death.attack.experiment_043`。规格见 `gametest/Experiment043Tests`：预热恰好三次、火焰 61 拍与 13 拍伤害、141 tick 后自行停止、猪（无护甲）每拍损失 4 点并点燃 300 tick、第二拍仍然可以穿透无敌帧造成伤害、14 格内受到伤害而 14 格外不受伤害、中途移动位置并转身时火焰随之移动、施放期间换走物品即切换到脚下向上喷射并烧灼自身、背后、侧面与烈焰人都无法命中、单击扣除 1 点耐久并挂载冷却。

试验物品 066 是那本恶魂陷阱书：单击右键即可把暮色森林恶魂陷阱方块（`twilightforest:ghast_trap`）的压场那一段沿视线释放，实现位于 `item/GhastTrapProcess`。方块那一套位于 `GhastTrapBlockEntity#tickActive` 中：场是以方块为中心、横向 6 格、纵向 16 格的立方体，场内的生物**每 tick** 通过 `setDeltaMovement` 被拽向场心（`(场心 - 生物) * 0.1`），并有十分之一的随机概率受到一次伤害（10 点），运行 120 tick 后自行关闭，充能依靠 `tickInactive` 统计场边死去的砷铅铁恶魂幼体。此处去掉了充能，场替换为「从眼睛往前 16 格长、轴线外 4 格粗的走廊」：判定时把活体生物包围盒中心投影到「视线轴 × 右 × 上」三条轴上（与 043 同一套写法），沿轴坐标落在 0~16、两条横轴均在 4 格加包围盒半径以内即判定为在场内；牵引沿用 0.1 那一条且不设上限，方向朝向场心。伤害是**每 tick 调一次 `hurt`**，不额外安排节拍：原版 `LivingEntity#hurt` 的无敌帧机制（打出一击后 `invulnerableTime` 从 20 向下递减，在降至 10 之前再次攻击不计入）会把连续施压自动压缩为每 0.5 秒一次，120 tick 恰好 12 次，因此伤害类型**不进入** `#minecraft:bypasses_cooldown`——若绕过该机制，节奏便会消失（043 的喷火器相反需要绕过，因为它是逐拍的独立攻击）。作用对象是 `LivingEntity` 全体（怪物、动物与玩家都会受到牵引与伤害，因为这本书需要能够装到敌对生物身上用于攻击玩家）外加 `ItemEntity`（掉落物只受牵引而不损失生命值），仅排除施放者自身、它的同族（`entity/GhastKin`：本模组的两种恶魂与 `twilightforest:carminite_ghastling`；玩家施放时这一条等于不存在，两个玩家之间照常互伤）与观察者；同一个施放者也不会叠出第二个场；施放者类型就是 `LivingEntity`，怪物持有并调用 `GhastTrapProcess#start` 即可使用。伤害类型 `experiment_066` 只进入 `#minecraft:no_knockback`，否则伤害自带的击退会与牵引相互抵消。**暮色恶魂的免伤机制无法通过标签解除**：`UrGhast#hurt` 中存在 `if (isInTantrum()) amount /= 10`，退出 tantrum 需要累计实际扣除 18 点，而书每下 7 点，打两下即攒满，之后每下只剩 0.7（探针实测 40 tick 只扣除 15.4）；`isInvulnerableTo` 只识别 `IN_WALL` / `IS_FIRE` / `invulnerable` 旗标，本模组的类型本来就不涉及这些。因此在命中之前使用 `item/UrGhastPhaseSuppressor` 直接调用它的 `setInTantrum(false)` 与 `resetDamageUntilNextPhase()`（与恶魂陷阱方块的 `tickActive` 同款，只是后者每 tick 还会调用 `setInTrap()`），将其压制在相位之外，使每下都造成满额伤害；这两个方法都是 `public`，类型引用写在编译期，暮色森林改名时构建直接失败。粒子与音效并非自行选定，而是整份沿用那块方块的客户端分支：一拍五颗 `twilightforest:ghast_trap`（`vx = cos(counter/10) * 2.5`、`vy = 20`、`vz = sin(counter/10) * 2.5`，五颗依次为 `(vx,vy,vz)`、`(-vx,vy,-vz)`、`(-vx,vy/2,vz)`、`(vx/2,vy/4,vz/2)`、`(-vx/2,vy/4,-vz/2)`；方块那三个偏移中 `vy = 20` 是「吸口向」，朝向世界 Y（因此方块是从二十格高空向下收拢），而本书的吸口朝向视线，这一份传给 `direction`，另外两份分别传给右轴与上轴，臂因此从视线远处收进吸口；速度即出生偏移，粒子从「场心 + 偏移」处收向场心），`counter > 100` 之后每 4 tick 补一颗 `twilightforest:huge_smoke`（偏移 `cos*0.05, 0.25, sin*0.05`）；这些粒子从服务端发出，包中的 `count` 必须为 0——客户端那条分支调用 `addParticle(type, x, y, z, maxSpeed * xDist, maxSpeed * yDist, maxSpeed * zDist)`，速度按原值落地而不掺入随机，恰好对应方块客户端那六个参数。场心并不位于方块上，而是**施放者身上那个点**：眼睛往前 1 格（`TRAP_OFFSET`），每 tick 随视线转动，与 043 的枪口采用同一做法——因此走廊中的目标是被吸向**施放者**的，五颗一轮的粒子与三支音效也都从这一个场心发出。音效为三支 `block.twilightforest.ghast_trap.warmup` / `.on` / `.spindown`（`TFSounds` 的对应常量），与方块一样逐拍播放：0~29 tick 预热、30~79 通电、80 以后收场，音量 1、音高 4，`SoundSource.BLOCKS`。场每 tick 重新绑定到施放者的眼睛与视线（转身即随之转动，死亡与更换维度则中断），过程挂在 `ServerTickEvent.Post`（`DdtServerEvents`）上推进，冷却为 180 tick（整段 120 再加 3 秒），每次消耗 1 点耐久，共 64 点。规格见 `gametest/Experiment066Tests`：占位号已让出、64 点耐久且不可堆叠、冷却在第 179 tick 仍锁定而在第 180 tick 解锁、走廊中的目标在 120 tick 内落下 12 次伤害（100 → 16）、背后、侧面与 16 格以外不受到伤害、玩家在走廊中照常损失生命值而施放者自身不受影响、`twilightforest:ghast_trap` / `huge_smoke` 与三支音效均可取到、模型与三条语言键、伤害类型只在 no_knockback 而不在 bypasses_cooldown。

试验物品 045 是一面盾：`item/Experiment045Item` 直接继承原版 `ShieldItem`，格挡、每次格挡损失 1 点耐久、可用木板修复、只能进入副手、72000 tick 的 `UseAnim.BLOCK` 全部沿用原版机制；耐久为 336 点，与 `Items.SHIELD` 相同，`BANNER_PATTERNS` 也参照原版提供了默认空表。物品模型 `item/experiment_045` 挂载 `minecraft:builtin/entity`（`gui_light: front` 与原版盾那套 `display`），并按原版盾的 `blocking` 谓词切换到 `item/experiment_045_blocking`——该谓词在原版中只注册给 `Items.SHIELD`，客户端 setup 中为 045 补充了一条，举盾时的第一与第三人称变换才能正确切换。几何体直接使用原版 `ShieldModel`，真正绘制盾面的是 `client/Experiment045ShieldRenderer`（`BlockEntityWithoutLevelRenderer`，经 `RegisterClientExtensionsEvent` 挂载到物品上）：没有旗面图案时使用 `item/experiment_045_nopattern` 整面木盾，存在图案或底色时替换为 `item/experiment_045_base`（正面留白）并交给 `BannerRenderer#renderPatterns` 逐层叠加；发光层 `item/experiment_045_glow` 绘制在同一块正面上，采用 `RenderType#entityTranslucentEmissive` 渲染，并单独注册为固定渲染缓冲（`RegisterRenderBuffersEvent`）——固定缓冲按登记顺序冲刷，它排在原版那几条之后，因此发光层一定叠加在本体与旗面图案之上，不会被自身覆盖。三张贴图都位于 `textures/item/` 下，被原版 blocks 图集的目录源收录，`Material` 直接指向方块图集。规格见 `gametest/Experiment045Tests`：占位号已让出、是 `ShieldItem` 子类、336 点耐久且不可堆叠、`SHIELD_BLOCK` 能力放行且只能进入副手、木板可修复、模型为 `builtin/entity` 且带 `blocking` 覆盖、三张贴图都位于 jar 中。

试验物品 047~050 是一套护甲（047 头盔、048 胸甲、049 护腿、050 靴子），物品为 `item/ExperimentArmorItem`，它只为重写两处方法而存在：`getArmorTexture` 把护甲层的贴图指向 `textures/entities/experiment_armor.png`，`verifyComponentsAfterLoad` 移除 `TRIM` 组件（理由与绯色钼那套相同：纹饰层按原版 64×32 排列，叠加在这套 UV 上只会错位；锻造台那条路径本来也无法进入，因为本套不在 `#minecraft:trimmable_armor` 中）。数值位于 `ModArmorMaterials#EXPERIMENT_ARMOR`：护甲值与铁同级，每件提供 2 点韧性，附魔值为 9，耐久倍率为 15。维修材料是三样锭中的**任意一样**——`Ingredient.of` 接收的是候选清单，`Ingredient#test` 命中其中任意一个即成立，砷铅铁锭、绯红钼锭与䤧锭中任意一样都能维修，不需要三样齐备。

穿戴外观来自源文件中的那一份基岩版几何（`minecraft:geometry` 1.12.0，64×64 的 UV，头、胸、双臂、双腿、双脚共八根骨骼），四件共用，`client/model/ExperimentArmorModel` 按槽位拆成四份 `LayerDefinition`：头盔取头，胸甲取躯干与双臂（含两根倾角 22.5° 的上臂筒与肩外侧甲片），护腿取双腿，靴子取双脚（含一块旋转 90° 的脚背斜护片）。把基岩版几何搬进 `PartDefinition` 的换算有三条：基岩版原点位于脚底且 y 轴朝上，Java 模型的 y 轴朝下且头部枢轴位于 y=0 对应基岩版的 y=24（骨骼位姿为 `(px, 24-py, pz)`，方块最小角为 `(bx-px, py-by-sy, bz-pz)`）；带 `rotation` 的方块拆分为骨骼下的子 `ModelPart`；**旋转角度照搬基岩版的符号，不取负值**——肩甲那对 22.5° 与脚背护片那对 ±90° 都是右正左负（左件带 `mirror`），取负会使它们朝内倾斜。计算结果与原版玩家模型的基准位置完全一致（头身 0,0,0、臂 ±5,2,0、腿 ±1.9,12,0，臂与腿的方块也是原版那几组数值），因为那份几何本来就按原版人体比例编写。姿势与可见性不需要自行计算：`IClientItemExtensions#getGenericArmorModel` 会把护甲层刚计算好的那套 `copyModelProperties` 复制到缓存的模型上。

发光层由 `mixin/ExperimentArmorGlowMixin` 实现：原版护甲层对一件物品只绘制一遍，`renderArmorPiece` 中的循环遍历 `ArmorMaterial#layers()`，每一层都交给 `renderModel`，因此在 `renderArmorPiece` 的 TAIL 再补绘一遍，以同一份几何换用 `textures/entities/experiment_armor_glow.png`。渲染类型必须**仍然**是 `armorCutoutNoCull`：它带有 `VIEW_OFFSET_Z_LAYERING`，冲刷时把模型视图朝相机方向收缩 0.99975586，本体因此在深度上比真实位置更近约万分之二点四；若换成不具备该偏移的 `entityTranslucentEmissive`，同一份几何摆在同一位置会始终落后于本体，整层被盔甲覆盖，只有把相机深入模型内部才会在轮廓边缘露出一丝（此前的版本正是这一问题）。两个批次使用同一个 RenderType 状态，深度才相等，`LEQUAL` 才能通过，而发光层是后冲刷的那一批，因此稳定地压在本体之上。「发光」不依赖 emissive 着色器，而依赖顶点光照：`rendertype_armor_cutout_no_cull` 的 vsh 中 `texelFetch(Sampler2, UV2 / 16, 0)` 直接采样顶点光照图，传入 `LightTexture.FULL_BRIGHT` 后整层恒为满亮；其 fsh 中 `color.a < 0.1` 时直接 discard，发光图中透明的位置不会被涂黑。注解加在单个槽位那一层而不是 `render` 上，因此穿一件还是四件都不需要另写分支。规格见 `gametest/Experiment047Tests`：占位号已让出、四件各自占用正确槽位、护甲值为 2/6/5/2 且总计 15、韧性为 2、附魔值为 9、耐久照铁、三样锭逐一单独都能维修而铁锭与钻石不能、四件的内外层都返回那张专用图、本体图与发光图都位于 jar 中、四个物品模型各指向自己的图标。


## 装饰画

变体属于数据包注册表，条目位于 `data/dawnduskterminal/painting_variant/painting_ghast_1.json`，尺寸为 3 格宽、2 格高；`registry/ModPaintingVariants` 只放置指向它的 `ResourceKey`，尺寸不在 Java 侧重复声明。

贴图放置在 `assets/dawnduskterminal/textures/painting/painting_ghast_1.png`。原版 `atlases/paintings.json` 只声明了「扫描 `textures/painting` 目录」，该目录源跨命名空间收图，因此放入这个位置就会被 `minecraft:paintings` 图集收录，渲染走原版 `PaintingRenderer`，不需要自行编写。

物品图标的贴图必须另外放置一份到 `textures/item/`：1.21.1 的物品模型贴图从 `minecraft:paintings` 之外的 `blocks` 图集获取，而该图集只声明了 `block/` 与 `item/` 两个目录源，`textures/entity/` 下的图不会被收录，模型因此会渲染成紫黑格子（`Missing textures in model ...`）。

`entity/GhastPaintingEntity` 是独立的实体类型，不共用 `minecraft:painting`：原版画物品只从 `#minecraft:painting_variant/placeable` 中挑选变体，而本模组的变体不进入该标签，因此原版物品无法放置它；该实体又把 `setVariant` 固定为自己的变体，敲碎后掉落的是 `painting_ghast_1` 物品，中键选取得到的也是它。

`mixin/PaintingVariantGuardMixin` 拦截的是手工修改这条旁路：原版画实体上的 `setVariant` 遇到该变体时直接忽略，因此无论通过 `/summon` 指定变体，还是修改旧存档中的 NBT，都无法得到本模组的画。

`item/GhastPaintingItem` 的摆放流程参照 `HangingEntityItem` 编写，只能放置这一幅画，且没有配方。

## 维度与晨昏线

`registry/ModDimensions.CHRONO` 是维度的 `ResourceKey`。

`world/ChronoLine` 是一个 record，存储原点与单位法向。

`ChronoLine.distance(x,z)` 计算点到该线的有符号距离。

`ChronoLine.param(x,z)` 计算距离除以最大距离的结果，并钳制到 -1 到 1。

`ChronoLine.create(random)` 为每个世界随机生成一次法向，基准朝东，抖动范围为正负 30 度，再随机决定是否旋转 180 度朝西。

配置项 `normal_override` 可以固定法向。

`world/ChronoLineState` 继承 `SavedData`，存储在主世界，键为 `dawnduskterminal_chrono_line`。

服务端与客户端各有一份静态缓存 `serverLine` 与 `clientLine`。

`network/ChronoLinePayload` 在玩家登录时把线下发到客户端。

法向每个维度只计算一次，多个玩家共用同一条线。

## 群系源

`world/PaletteBiomeSource` 是自行编写的 `BiomeSource`，注册为 `dawnduskterminal:palette`。

它要求提供一个 `delegate` 与一个 `palette` 群系标签。

`preset` 是原版主世界参数表，`extra` 是追加在表尾的自建气候点（极昼与极夜），选格使用原版那套 `ParameterList.findValue`。

`getNoiseBiome` 先执行 delegate，再把 `minecraft:*` 群系替换为同名副本。

副本名与命名空间的对应关系按路径匹配，例如 `minecraft:plains` 替换为 `dawnduskterminal:plains`。

`extra` 的条目按温度出界来编写：原版参数表只覆盖 `[-1, 1]`，出界的跨度一经写出即生效。温度由 `ChronoTemperature` 在极带推到 `±2`。

某一格可以在替换表中再次改指：`minecraft:desert` 替换为白地（昼区）。这只决定那一格装载哪个副本，选格仍然由气候表计算。

`floatrees` 群系走另一条路径，采样深度低于阈值时直接返回。

群系源中没有任何按 `param` 判定群系的特例，极带暂时与相邻带使用同一群系。

替换表延迟构建，因为 biome source 在数据包解析期创建，此时标签中的 holder 还没有 key。

细节见 `worldgen.md`。

## 世界生成补丁

数据包无法覆盖的原版硬编码部分由 `mixin/` 接管。

`LavaBandPaletteMixin` 把 `createFluidPicker` 中 y 以下的岩浆带替换为热情的暮光。

`LavaAquiferPaletteMixin` 把深部岩浆含水层替换为热情的暮光。

`PillarsFluidBlockMixin` 使石柱洞穴保持干燥。

`NoiseChunkVeinPaletteMixin` 使用 `world/ChronoVeinPalette` 把大矿脉中的花岗岩与凝灰岩替换为赭石与暮光石。

温度的晨昏线偏移不在此处实现，而是由 `world/PaletteBiomeSource` 与 `world/ChronoTemperature` 负责，见「世界生成补丁」。

`MineshaftHeightMixin` 把废弃矿井的高度范围限制在 y -64 到 -10。

`MineshaftChestLootMixin` 把矿井箱车战利品替换为 `dawnduskterminal:chests/chrono_mineshaft`。

识别维度统一依靠噪声设置的 `default_fluid` 是否为 `portal_fluid`。

## 传送门

`portal/PortalShape` 负责水池识别与判定。

`portal/PortalBuilder` 把水池方块替换为传送门流体。

`portal/PortalTrigger` 判定实体是否处在可用的传送门中。

`portal/PortalBlock` 是传送门流体的方块，负责处理方块内的实体与自愈。

`portal/PortalTeleporter` 负责即时传送、落点搜索与到达门。

`portal/PortalState` 是玩家附件，存储回程维度与坐标。

细节见 `portal.md`。

## 流体

`registry/ModFluids` 注册 `portal_fluid`，流动参数参照水。

`registry/ModScorchingTwilight` 注册 `enthu_fluid`（热情的暮光）的类型、流体、方块与桶。

`world/FluidReactions` 在 `FMLCommonSetupEvent` 上把接触反应注册进 NeoForge 的 `FluidInteractionRegistry`。

`block/ScorchingTwilightBlock` 继承岩浆的方块属性，但不会引燃，也不会清除植物，接触时造成 6 点 `enthu_fluid` 伤害。

`item/CanocanItem` 是唯一能够舀取这两种流体的容器，原版桶会被销毁。

## 进度与效果

`progression/BossKind` 枚举三个暮色森林 Boss 与对应的战利品。

`progression/BossProgress` 是 record，存储三个击败标记。

`progression/BossProgressTracker` 在击杀事件中写入附件。

附件定义在 `registry/ModAttachments` 中，`boss_progress` 会被序列化并设置 `copyOnDeath`。

`registry/ModEffects` 注册 `stability` 与 `portal_sickness`。

`world/StabilityEffect` 是纯标记效果，方向由 `PortalBlock` 决定。

`advancement/LineParamTrigger` 是自定义判定，按晨昏线投影参数给予进度。

## 客户端

`client/DdtClient` 是客户端入口，负责注册模型、颜色、渲染层与事件。

`client/ChronoFoliageColors` 复刻暮色森林的树叶染色算法。

`client/model/MountedTowerWoodModel` 实现镶边塔木、封装塔木梁、錾制塔木梁与警示塔木的连接模型，「47 格」索引公式位于 `client/model/ConnectedTexture47`。

`client/model/CountertopModel` 使用同一张 8x8 图集绘制黑木台面的顶面，只裁剪顶面那一格，连接判定为「邻居是任意一种台面」。

`client/PortalFluidExtensions` 与 `ScorchingTwilightFluidExtensions` 是流体的客户端扩展。

`DdtClient` 注册方块与物品颜色，草与树叶随群系染色。

天空全部交给原版渲染器，维度特效直接复用 `minecraft:overworld`，见「天空」。

## Mixin

配置为 `dawnduskterminal.mixins.json`，分为公共与客户端两份。

`FluidStatusAccessor` 向其他 mixin 暴露 `Aquifer.FluidStatus` 的流体类型。

`LavaBandPaletteMixin`、`LavaAquiferPaletteMixin` 与 `PillarsFluidBlockMixin` 见「世界生成补丁」。

`NoiseChunkVeinPaletteMixin` 见「世界生成补丁」。

`MineshaftHeightMixin` 与 `MineshaftChestLootMixin` 见「世界生成补丁」。

`FeatureSorterMixin` 把原版特征排序替换为可容忍环的实现，受配置项 `tolerate_feature_cycles` 控制。

`LightEngineSkyLightMixin` 在晨昏线维度把天光上限压低到 `ChronoSkyLight.maxSkyLight`。

`StructureExclusionMixin` 在晨昏线维度只放行结构白名单中的结构。

`BiomeColorsFoliageMixin` 是客户端 mixin，把树叶染色接入 `ChronoFoliageColors`。

`ClientLevelTimeOfDayMixin` 与 `LevelRendererSkyAlignMixin` 是客户端 mixin，见「天空」。

`ClientLevelSkyColorMixin` 是客户端 mixin，见「天空」。

## 天空

自绘天空已经移除，天空全部由原版 `LevelRenderer.renderSky` 处理。

维度特效直接复用 `minecraft:overworld`，雾色、日月、星空、日落光晕与云都是原版的，`dimension_type/chrono.json` 的 `effects` 因此不再是模组自己的键。

`ClientLevelTimeOfDayMixin` 在晨昏线维度把 `getTimeOfDay` 替换为 `0.25 - param * sun_angle_max / 360`，原版那一整套渲染因此随玩家到晨昏线的距离变化，太阳仰角仍为 `param * sun_angle_max`。

原版日月轨道始终落在 X 轴上，`LevelRendererSkyAlignMixin` 在 `renderSky` 的两处 `pushPose` 之后追加一层绕世界 Y 轴的旋转，把轨道与日落光晕旋转到晨昏线法向上。

`fixed_time` 仍保留在维度类型中，它只影响服务端的 `getTimeOfDay`，客户端渲染不再读取它。

`ClientLevelSkyColorMixin` 把晨昏线维度的 `getSkyColor` 乘以 0.75。原版日月采用加色混合，而极昼侧自定义群系的 `sky_color` 接近白色（`white_land` 为 `FFF9F0`），加到纯白上仍然是纯白，太阳因此与天空混为一体、无法区分。天穹色同时决定雾色，所以这一处理也降低了过曝的雾。常数位于 mixin 中，调小则画面更暗。

## 事件

`server/DdtServerEvents` 挂载到 `NeoForge.EVENT_BUS`。

`onServerStarted` 读取并记录晨昏线，同时校验结构与摆放表。

`onPlayerLoggedIn` 下发晨昏线包。

`onPlayerTick` 每 10 tick 扫描玩家附近的投料物，并在玩家进入维度后触发进度判定。

`onLivingDeath` 记录 Boss 击杀。

`onRightClickItem` 拦截原版桶，禁止舀取两种模组流体。

## 结构

`structure/StructureExclusions` 是唯一白名单，`StructureExclusionMixin` 排除不在其中的结构。

`structure/StructureExclusions` 中的 `UNMAPPED` 与 `LANDMARKS` 记录未放行项与地标，仅用于日志。

服务端启动时校验这几张表，缺失时只记录日志。

## 已知遗留

`registry/ModEffects` 仍注册 `portal_sickness`，但已经没有任何调用方施加该效果，方向改由 `stability` 决定。

`config/DdtConfig` 的 `portal.cooldown_ticks` 与 `portal.entity_debuff` 已经没有读取方。

语言文件中 `death.attack.dawnduskterminal.portal` 与 `message.dawnduskterminal.searching` 已不再使用。
