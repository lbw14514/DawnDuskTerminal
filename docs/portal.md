# 传送门与流体

传送门由水池 池边植物 投料物三者构成

判定与传送在 `portal/` 包

两种模组流体是门与危险源，反应在 `world/FluidReactions`

## 标签

`tags/block/portal/fluid` 定义可用作水池的方块，当前只有 `minecraft:water`

`tags/block/portal/edge` 定义可种植的池边方块

`tags/block/portal/decoration` 定义可算作植物的方块，含空气类

## 建造判定

判定在 `portal/PortalShape.find`

种子方块必须在水池标签里

水池可能是更深水体的中间一层，判定从种子向上走完整个水体，用最上面那层当门（详见「生成传送门」）

从种子水平洪泛，只走同一 y 的水方块

水池方块数下限 `MIN_WATER` 4，上限 `MAX_WATER` 64，超过即失败

每列水向下最多找 16 格到达的地板，地板必须能站

所有水方块的水平邻居必须属于 `portal/edge` 标签

环上每个位置上方若属于 `portal/decoration` 且不是空气，记为一株植物

植物数须大于等于 `max(2, 环长 / 4)`

满足则返回 `PortalShape`，带水池 边框 植物三组坐标

## 生成传送门

`portal/PortalBuilder.build`

先把判定出的水池方块清成空气，再逐个替换成 `dawnduskterminal:portal_fluid`

已经是传送门流体的跳过

两趟是必须的：传送门流体一碰到水就会被流体反应变成 `fragile_canopy`，而反应在 `LiquidBlock#onPlace` 上立即生效，边清边灌的话先灌好的那些邻居还是水，会被反应吃掉

反应只扫正上方与四个水平邻居（`LiquidBlock.POSSIBLE_FLOW_DIRECTIONS` 取相反方向，不含正下方），所以水池下面的水无害，上面的水会吃掉刚填好的传送门流体。`PortalShape.find` 因此固定取水体最上面那层

## 触发

投料物是 `dawnduskterminal:encased_dust`

两条触发路径

物品实体落在水里时由 `PortalBlock.entityInside` 调 `tryIgnite`

玩家附近 12 格内落在水里的投料物由 `DdtServerEvents.scanFuelItems` 每 10 tick 扫描并调 `tryIgnite`

`tryIgnite` 先调 `build`，成功才消耗一个物品

成功后触发 `build_gate` 进度，召雷，放粒子，放 `portal_open` 音效

## 传送门方块

`portal/PortalBlock` 继承 `LiquidBlock`

`randomTick` 里若四周没有相邻传送门方块就变回空气，实现自愈

`isCompletePortalPart` 要求相邻传送门方块至少 2 个

`PortalTrigger.shouldTeleport` 判定实体是否处在完整传送门里，玩家游泳时不触发

## 即时传送

`portal/PortalTeleporter.teleportEntity`

进入方块的下一个 tick 就传送，没有蓄力 没有冷却

当前维度不是晨昏线维度时目标是晨昏线维度，否则是主世界

传送到目标后播 `minecraft:portal_travel`

落点搜索 `findSafeLanding`

先用原点列，再按 16 格环扩到半径 320，最多加载 64 个区块，并受 8ms 软时间预算限制

单列判定用 `MOTION_BLOCKING_NO_LEAVES` 表面，表面须在 0 到 128，下方能站，脚部无碰撞且无流体

搜不到或超出时间预算时铺一块半径 6 的兜底平台，用霞草 霞土 苍穹石

进入维度时在落点附近建到达门 `buildArrivalPortal`

先在 24 格环内找平缓地面（4ms 预算），铺半径 4 的苍穹石台面

台面上开 2 乘 2 的传送门流体池，池边铺霞草与霞土

回程不建新门，直接传送到记住的坐标

## 回程坐标

存在玩家附件 `portal_state`，字段 `returnDim` 与 `x y z`

进维度时写入当前维度与坐标

## 稳定效果

效果 `dawnduskterminal:stability`，纯标记，方向由 `PortalBlock.entityInside` 决定

玩家有稳定则不过门，非玩家生物有稳定则过门

不过门且受到伤害时累计，每 40 tick 造成 2 点 `stability` 伤害并播 `portal_hurt`

药水 `dawnduskterminal:stability` 给 8 分钟（9600 tick），物品 `dawnduskterminal:stability_potion` 走通用药水瓶外观（模型继承 `minecraft:item/potion`），紫色不是贴图上画的，客户端按 `potion_contents` 取色，取出来就是效果色 `0x7F159E`，与传送门流体的雾色同值；暂无配方

## 两种流体

`dawnduskterminal:portal_fluid` 是传送门水体与维度的默认流体

流动参数照抄原版水，但不无限生成源，也不吞掉外来水

`dawnduskterminal:enthu_fluid`（热情的暮光）借用岩浆的方块属性，但不点火 不清植物

接触它的生物每受击窗口受 6 点 `enthu_fluid` 伤害

## 流体反应

反应用 NeoForge `FluidInteractionRegistry` 注册，在 `FMLCommonSetupEvent` 上执行

源流体一侧被替换为产物，邻居不动

`portal_fluid` 加水得 `fragile_canopy`

`portal_fluid` 加岩浆得 `chaos_stone`

`portal_fluid` 加热情的暮光得 `canopy_stone`

热情的暮光加水得暮色森林的 `aurora_block`，取不到时退化为 `blazing_block`

热情的暮光加岩浆得 `blazing_block`

## 桶

原版桶舀不动这两种流体

`DdtServerEvents.onRightClickItem` 拦截，取消交互，播物品破碎音，销毁一个桶

`dawnduskterminal:canocan` 是唯一能舀的容器

舀 `portal_fluid` 得 `portal_bucket`，舀热情的暮光得 `enthu_fluid_bucket`

## 音效

`portal_open` 开门

`portal_enter` 进入 暂借 `portal_open` 的音频

`portal_exit` 离开 暂借 `portal_open` 的音频

`portal_hurt` 受侵蚀

`chrono_ambient` 维度环境音 暂借 `carminite_field` 的音频

## 命令

`/ddt line` 打印当前位置的原点 法向 距离 参数 天光 维度

`/ddt tp <param>` 按投影参数在 -1 到 1 之间传送到维度内

需要权限等级 2

## 已知遗留

`ModEffects` 仍注册 `portal_sickness`，但没有代码再施加它

语言文件里 `message.dawnduskterminal.searching` 已不再使用
