# 合成与标签

苍穹石必须能够充当石头与圆石使用，这样在维度中取得的石头才可以适用于整套原版石头配方。

配方层面的实现比看上去更为曲折，原因有三个，具体见下文。

## 一 只加标签不会生效

1.21.1 的物品标签中**没有** `minecraft:stone` 与 `minecraft:cobblestone`。

原版配方把「石头」与「圆石」当作物品写死在 `key` 与 `ingredient` 之中，例如：

```json
"key": { "#": { "item": "minecraft:stone" } }
```

在整份原版数据中，只有两条石头材料是通过标签指定的：

- `minecraft:stone_crafting_materials`，用于熔炉与酿造台；
- `minecraft:stone_tool_materials`，用于石制工具五件。

因此，即便向自建的 `minecraft:stone` 标签中加入一个方块，也不会有任何配方识别它。

## 二 本模组的做法

本模组在 `data/minecraft/tags/item/` 下提供四条标签。

| 标签 | 内容 | 谁在用 |
|---|---|---|
| `minecraft:stone` | `minecraft:stone` 与 `canopy_stone` | `data/minecraft/recipe/` 下改写过的原版配方 |
| `minecraft:cobblestone` | `minecraft:cobblestone` 与 `canopy_stone` | 同上 |
| `minecraft:stone_crafting_materials` | 追加 `canopy_stone` | 原版熔炉与酿造台配方 |
| `minecraft:stone_tool_materials` | 追加 `canopy_stone` | 原版石制工具配方 |

`data/minecraft/recipe/` 下的 22 个文件是原版配方的副本，其中只把 `{"item": …}` 替换为 `{"tag": …}`。由于同名路径会覆盖原版数据包，因此游戏中生效的是改写后的版本。

- 合成台：石头砖、石台阶、石楼梯、石按钮与石压力板，以及活塞所用的红石中继器与比较器等；
- 石切机：石头系的七个 `from_stone` 配方，以及圆石系的三个 `from_cobblestone` 配方；
- 熔炉：圆石烧制石头，石头烧制平滑石头。

产物仍然是原版方块，苍穹石只顶替原料位，不改变产物。

## 三 NeoForge 的数据包优先级高于模组数据包

NeoForge 自带 191 个原版配方覆盖，其中 13 条与本模组需要修改的配方重合，分别是 `andesite`、`diorite`、`dispenser`、`dropper`、`lever`、`observer`、`piston`、`mossy_cobblestone_from_vine`、`mossy_cobblestone_from_moss_block`、`coast_/sentry_/vex_armor_trim_smithing_template` 与 `stonecutter`。

在这 13 条配方中，NeoForge 已经把圆石替换为通用标签 `c:cobblestones/normal`，但其数据包优先级高于模组数据包，因此即便写出同名文件也不会生效。

于是改为从标签侧入手，在 `data/c/tags/item/` 下补充两条标签：

- 在 `c:cobblestones/normal` 中追加 `canopy_stone`；
- 在 `c:stones` 中追加 `canopy_stone`。

至于 `stonecutter.json` 那一条，NeoForge 仍然写死 `minecraft:stone`，通过标签无法补救，因此由 `data/dawnduskterminal/recipe/canopy_stone_stonecutter.json` 补充一条模组自身的配方。

需要记录以下结论：再次实现「某方块顶替原版材料」时，应先查明 NeoForge 覆盖了哪些原版配方（对应 `neoforge-<版本>-universal.jar` 中的 `data/minecraft/recipe/`）；若无法覆盖，则改为修改标签，或补充自己的配方。

## 四 测试

`gametest/RecipeTagTests` 包含四条测试，通过 `./gradlew runGameTestServer` 运行：

1. 六条标签都认得苍穹石。
2. 石头系原版配方逐条都能被苍穹石顶替。
3. 圆石系原版配方逐条都能被苍穹石顶替。
4. 存在使用苍穹石制作出石切机的方法。

断言通过 `RecipeManager` 取得的是**最终生效**的那一版配方，而不检查本模组写了哪些文件。因此，若 NeoForge 以后增加或删除覆盖，或者本模组遗漏某一个改写文件，这些测试都会失败。

## 五 天门松木家族

天门松照苍穹木配齐：原木、去皮原木、木、去皮木、空心原木。木的六面都用 `block/skygate_pine_log` 张贴图，不单独出一张，与 `minecraft:oak_wood` 的做法相同。

| 文件 | 内容 |
|---|---|
| `recipe/skygate_pine_wood.json` | 4 原木 → 3 木 |
| `recipe/stripped_skygate_pine_wood.json` | 4 去皮原木 → 3 去皮木 |
| `recipe/hollow_skygate_pine_log.json` | 石切机，原木 → 空心原木 |

去皮走 `data/neoforge/data_maps/block/strippables.json`：原木→去皮原木、木→去皮木两条映射都写在那里。

标签分三层，缺一层就会出现「配方不认、斧子不快、僵尸门都拆不动」这类问题。

| 标签 | 内容 |
|---|---|
| `minecraft:logs` 与 `minecraft:logs_that_burn`（方块与物品各一份） | 原木、去皮原木、木、去皮木 |
| `minecraft:stripped_logs`（方块与物品各一份） | 去皮原木、去皮木 |
| `minecraft:mineable/axe` | 上述四块加三块空心原木 |
| `minecraft:climbable` | 攀爬版空心原木；缺了它挂上梯子也爬不动 |
| `dawnduskterminal:skygate_pine_logs`（方块与物品各一份） | 原木家族四块，照暮色森林按树种分标签的习惯 |
| `dawnduskterminal:hollow_logs` | 三块空心原木 |

## 六 空心原木

照暮色森林移植：一块「木头」实际是三个方块，共用一个物品 `hollow_skygate_pine_log`。

| 方块 | 状态 | 说明 |
|---|---|---|
| `hollow_skygate_pine_log_horizontal` | `axis` + `variant`（`empty` `moss` `moss_and_grass` `snow` `waterlogged`） | 躺着的空心原木，能钻进去 |
| `hollow_skygate_pine_log_vertical` | `waterlogged` | 立着的空心原木，从顶上跳进去 |
| `hollow_skygate_pine_log_climbable` | `facing` + `variant`（`vine` `ladder` `ladder_waterlogged`） | 挂藤或梯子的那面 |

- 放置：`item/HollowLogItem` 按点击面分派，点顶面或底面放竖向那块，点侧面放横向那块。
- 挖下来的掉落：`BlockItem#registerBlocks` 把三块都映射回同一件物品，因此不会出现三种掉落物。
- 横向那块往洞里塞苔藓毯、草、雪球分别切到 `moss`、`moss_and_grass`、`snow`，铁锹铲雪、剪刀收苔藓退回 `empty`。
- 竖向那块往洞里塞藤或梯子会整块换成攀爬版，剪掉再换回来。
- 苔藓与积雪直接取暮色森林的贴图：`twilightforest:block/mosspatch`、`moss_overhang`、`snow_overhang`。塞苔藓用的是暮色森林的 `moss_patch` 物品，剪刀回收的也是它，因此不需要另画贴图。
- 与暮色森林的差别只剩水的表示：这边直接用 `variant` 或 `waterlogged` 这一档，那边另有一层接口在三种方块之间搬水。
- 攀爬版必须进 `minecraft:climbable` 标签。梯子只是模型里的一层薄板，方块本身没有爬行逻辑，标签缺了这一项就只看得到梯子、爬不上去。

`SkygateWoodTests` 断言放置分派与掉落映射，`SkygatePineTests` 断言树上的枝用的是木。
