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
