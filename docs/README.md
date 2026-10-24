# 文档索引

本目录是开发文档

玩家向说明在仓库根目录的 `README.md`

历史笔记在 `DEVNOTES.md`，部分内容已过时

## 阅读顺序

快速上手先读 `architecture.md` 再看 `development.md`

只改地形读 `worldgen.md`

只换群系或调地表方块读 `biomes.md`

只改传送门与流体读 `portal.md`

只改合成与标签读 `crafting.md`

要调数值读 `configuration.md`

## 文件

`architecture.md` 模块职责 关键类 注册流程 事件 网络 Mixin 已知遗留

`worldgen.md` 维度类型 噪声设置 调色板群系源 地表规则 特征移植 结构白名单

`biomes.md` 占位群系对照 每个原版格子由谁占 方块命名对照 还没落地的名字

`issues.md` 已知问题清单 P1 P2 与修复状态

`portal.md` 传送门判定 触发 即时传送 落点门 稳定效果 流体与桶

`crafting.md` 苍穹石顶替石头与圆石 标签布局 原版配方改写 NeoForge 覆盖的坑 测试

`development.md` 环境 构建 运行 游戏测试 指令 CI 移植脚本 踩坑

`configuration.md` 全部配置键 默认值 作用

## 相关文件

根目录 `AGENTS.md` 面向 AI 与协作者的最短上手路径

根目录 `README.md` 面向玩家

根目录 `DEVNOTES.md` 历史笔记，部分已过时

`tools/` 把原版世界生成与暮色森林群系移植进本模组的脚本

`tools/ysm/` 把 Yes Steve Model 的 Bedrock 动画转换成 GeckoLib 可编译的形式，说明与实测数据见它目录下的 `README.md`
