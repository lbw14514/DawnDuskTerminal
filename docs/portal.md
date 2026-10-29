# 传送门与流体

传送门由水池、池边植物与投料物三者共同构成。

传送门的判定与传送逻辑均位于 `portal/` 包中。

两种模组流体既是门的构成材料，也是危险源，其反应定义在 `world/FluidReactions` 中。

## 标签

标签 `tags/block/portal/fluid` 定义可用作水池的方块，当前只包含 `minecraft:water`。

标签 `tags/block/portal/edge` 定义可种植的池边方块。

标签 `tags/block/portal/decoration` 定义可算作植物的方块，其中包含空气类方块。

## 建造判定

判定逻辑实现在 `portal/PortalShape.find` 中，种子方块必须属于水池标签。

水池可能是更深水体的中间一层，因此判定会从种子向上遍历整个水体，并以最上面那一层作为门（详见「生成传送门」一节）。

判定从种子出发进行水平洪泛，只遍历位于同一 y 坐标的水方块。

水池方块数量的下限为 `MIN_WATER`，即 4 个；上限为 `MAX_WATER`，即 64 个，超出上限即判定失败。

每一列水向下最多探测 16 格并据此寻得地板，该地板必须是可站立方块。

所有水方块的水平邻居必须属于 `portal/edge` 标签。

环上每个位置的上方若属于 `portal/decoration` 且不是空气，则记为一株植物。

植物数量必须大于或等于 `max(2, 环长 / 4)`。

满足以上全部条件时返回 `PortalShape`，其中携带水池、边框与植物三组坐标。

## 生成传送门

生成逻辑实现在 `portal/PortalBuilder.build` 中。该过程先把判定出的水池方块清除为空气，再逐个替换为 `dawnduskterminal:portal_fluid`，已经是传送门流体的方块则跳过。

该过程必须分两趟执行：传送门流体一旦接触水，就会被流体反应转换为 `fragile_canopy`，而该反应在 `LiquidBlock#onPlace` 中立即生效。若在清除水的同时灌注传送门流体，则先完成灌注的位置其邻居仍为水，刚灌注的流体将被该反应消耗。

该反应只扫描正上方与四个水平邻居（`LiquidBlock.POSSIBLE_FLOW_DIRECTIONS` 取相反方向，不含正下方），因此水池下方的水无害，而上方的水会消耗刚填充好的传送门流体。基于这一原因，`PortalShape.find` 固定选取水体最上面那一层。

## 触发

投料物是 `dawnduskterminal:encased_dust`，共有两条触发路径。

第一条路径是：物品实体落入水中时，由 `PortalBlock.entityInside` 调用 `tryIgnite`。

第二条路径是：玩家附近 12 格内落入水中的投料物，由 `DdtServerEvents.scanFuelItems` 每 10 tick 扫描一次并调用 `tryIgnite`。

`tryIgnite` 先调用 `build`，只有在构建成功之后才消耗一个物品。

触发成功之后会触发 `build_gate` 进度，并召雷、生成粒子、播放 `portal_open` 音效。

## 传送门方块

`portal/PortalBlock` 继承 `LiquidBlock`。

在 `randomTick` 中，若四周没有相邻的传送门方块，该方块就变回空气，从而实现自愈。

`isCompletePortalPart` 要求相邻的传送门方块至少有 2 个。

`PortalTrigger.shouldTeleport` 判定实体是否处在完整的传送门之中，玩家游泳时不会触发。

## 即时传送

传送逻辑实现在 `portal/PortalTeleporter.teleportEntity` 中：实体进入方块后的下一个 tick 即被传送，既没有蓄力过程，也没有冷却。

当前维度不是晨昏线维度时，目标为晨昏线维度；否则目标为主世界。

传送到目标位置之后播放 `minecraft:portal_travel`。

落点搜索由 `findSafeLanding` 完成：先使用原点所在列，再按 16 格环扩展至半径 320，最多加载 64 个区块，并受 8ms 软时间预算限制。

单列判定使用 `MOTION_BLOCKING_NO_LEAVES` 表面，该表面必须位于 0 到 128 之间，其下方为可站立方块，脚部无碰撞且无流体。

若搜索不到合适落点或超出时间预算，则铺设一块半径为 6 的回退平台，所用方块为霞草、霞土与苍穹石。

进入维度时，在落点附近建造到达门 `buildArrivalPortal`：先在 24 格环内寻找平缓地面（预算 4ms），铺设半径为 4 的苍穹石台面；随后在台面上开凿 2 乘 2 的传送门流体池，并在池边铺设霞草与霞土。

回程不建造新门，而是直接传送到记住的坐标。

## 回程坐标

回程坐标保存在玩家附件 `portal_state` 中，其字段为 `returnDim` 与 `x y z`。

进入维度时写入当前维度与坐标。

## 稳定效果

效果 `dawnduskterminal:stability` 是纯标记，其作用方向由 `PortalBlock.entityInside` 决定：玩家持有该稳定效果时不过门，非玩家生物持有该稳定效果时则过门。

若实体不过门并且受到伤害，则累计该伤害；每 40 tick 造成 2 点 `stability` 伤害，并播放 `portal_hurt`。

药水 `dawnduskterminal:stability` 提供 8 分钟（9600 tick）的效果。物品 `dawnduskterminal:stability_potion` 采用通用药水瓶外观（模型继承 `minecraft:item/potion`），其紫色并非绘制在贴图上，而是由客户端按 `potion_contents` 取色，取出的值即效果色 `0x7F159E`，与传送门流体的雾色同值；该物品暂无配方。

## 两种流体

`dawnduskterminal:portal_fluid` 是传送门水体，同时是维度的默认流体。其流动参数沿用原版水，但不会无限生成源，也不会吞没外来的水。

`dawnduskterminal:enthu_fluid`（热情的暮光）借用岩浆的方块属性，但不会点火，也不会清除植物。

接触该流体的生物在每个受击窗口承受 6 点 `enthu_fluid` 伤害。

## 流体反应

流体反应通过 NeoForge 的 `FluidInteractionRegistry` 注册，并在 `FMLCommonSetupEvent` 上执行。

反应发生时，源流体一侧被替换为产物，邻居保持不变。

`portal_fluid` 与水反应生成 `fragile_canopy`。

`portal_fluid` 与岩浆反应生成 `chaos_stone`。

`portal_fluid` 与热情的暮光反应生成 `canopy_stone`。

热情的暮光与水反应生成暮色森林的 `aurora_block`，取不到该方块时退化为 `blazing_block`。

热情的暮光与岩浆反应生成 `blazing_block`。

## 桶

原版桶无法舀取这两种流体。`DdtServerEvents.onRightClickItem` 会拦截该交互，取消交互、播放物品破碎音效，并销毁一个桶。

`dawnduskterminal:canocan` 是唯一能够舀取这两种流体的容器：舀取 `portal_fluid` 得到 `portal_bucket`，舀取热情的暮光得到 `enthu_fluid_bucket`。

## 音效

`portal_open` 用于开门。

`portal_enter` 用于进入，暂借用 `portal_open` 的音频。

`portal_exit` 用于离开，暂借用 `portal_open` 的音频。

`portal_hurt` 用于受侵蚀。

`chrono_ambient` 是维度环境音，暂借用 `carminite_field` 的音频。

## 命令

`/ddt line` 打印当前位置的原点、法向、距离、参数、天光与维度。

`/ddt tp <param>` 按投影参数在 -1 到 1 之间传送到维度内。

该命令需要权限等级 2。

## 已知遗留

`ModEffects` 仍注册 `portal_sickness`，但已没有代码再施加该效果。

语言文件中的 `message.dawnduskterminal.searching` 已不再使用。
