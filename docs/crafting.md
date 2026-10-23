# 合成与标签

苍穹石要能当石头与圆石用 这样维度里拿到的石头可以走完原版那一整套石头配方

配方层面的实现比看上去绕 原因有三个 见下

## 一 只加标签不会生效

1.21.1 的物品标签里**没有** `minecraft:stone` 与 `minecraft:cobblestone`

原版配方把「石头」「圆石」当物品写死在 `key` 与 `ingredient` 里 例如

```json
"key": { "#": { "item": "minecraft:stone" } }
```

整份原版数据里只有两条石头材料是走标签的

- `minecraft:stone_crafting_materials` 熔炉 酿造台
- `minecraft:stone_tool_materials` 石制工具五件

所以往自造的 `minecraft:stone` 标签里塞一个方块 不会有任何配方认得它

## 二 本模组的做法

四条 `data/minecraft/tags/item/` 下的标签

| 标签 | 内容 | 谁在用 |
|---|---|---|
| `minecraft:stone` | `minecraft:stone` 加 `canopy_stone` | `data/minecraft/recipe/` 下改写过的原版配方 |
| `minecraft:cobblestone` | `minecraft:cobblestone` 加 `canopy_stone` | 同上 |
| `minecraft:stone_crafting_materials` | 追加 `canopy_stone` | 原版熔炉与酿造台配方 |
| `minecraft:stone_tool_materials` | 追加 `canopy_stone` | 原版石制工具配方 |

`data/minecraft/recipe/` 下 22 个文件是原版配方的副本 只把 `{"item": …}` 换成 `{"tag": …}`
同名路径压过原版数据包 所以游戏里生效的是改写版

- 合成台 石头砖 石台阶 石楼梯 石按钮 石压力板 活塞用的红石中继器与比较器 等等
- 石切机 石头系的七个 `from_stone` 配方 加圆石系三个 `from_cobblestone` 配方
- 熔炉 圆石烧石头 石头烧平滑石头

产物仍是原版方块 苍穹石只顶替原料位 不改变产物

## 三 NeoForge 的数据包压得住模组的数据包

NeoForge 自己带 191 个原版配方覆盖 其中 13 条与本模组要改的重合

`andesite` `diorite` `dispenser` `dropper` `lever` `observer` `piston`
`mossy_cobblestone_from_vine` `mossy_cobblestone_from_moss_block`
`coast_/sentry_/vex_armor_trim_smithing_template` `stonecutter`

这 13 条里 NeoForge 已经把圆石换成通用标签 `c:cobblestones/normal`
但它的数据包优先级高于模组数据包 所以同名文件我们写了也不生效

于是改成从标签侧下手 在 `data/c/tags/item/` 下补两条

- `c:cobblestones/normal` 追加 `canopy_stone`
- `c:stones` 追加 `canopy_stone`

`stonecutter.json` 那条 NeoForge 仍写死 `minecraft:stone` 标签救不了
由 `data/dawnduskterminal/recipe/canopy_stone_stonecutter.json` 补一条模组自己的配方

记这条结论 以后再加「某方块顶替原版材料」时先查 NeoForge 覆盖了哪些原版配方
在 `neoforge-<版本>-universal.jar` 的 `data/minecraft/recipe/` 里 压不过就改标签或补自己的配方

## 四 测试

`gametest/RecipeTagTests` 四条 跑 `./gradlew runGameTestServer`

1. 六条标签都认得苍穹石
2. 石头系原版配方逐条能被苍穹石顶替
3. 圆石系原版配方逐条能被苍穹石顶替
4. 有办法用苍穹石做出石切机

断言走 `RecipeManager` 拿到的是**最终生效**的那一版配方 不看我们写了什么文件
所以 NeoForge 以后增删覆盖、或本模组漏写一个改写文件 这里都会红
