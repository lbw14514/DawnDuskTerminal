# DawnDuskTerminal 开发要点

## 项目位置
- 源码 `c:\Users\admin\Desktop\ysm\DawnDuskTerminal`
- 部署点 `C:\pcl2\lm2\neo\.minecraft\versions\1.21.1-NeoForge_21.1.252\mods\dawnduskterminal-0.1.0.jar` 和 `C:\Users\admin\Desktop\dawnduskterminal-0.1.0.jar`
- 截图目录 `C:\pcl2\lm2\neo\.minecraft\versions\1.21.1-NeoForge_21.1.252\screenshots`
- 文档 `c:\Users\admin\Desktop\ysm\md一类文件\`
- 生成地形的脚本 `c:\Users\admin\Desktop\ysm\gen_terrain.py`
- 换贴图脚本 `c:\Users\admin\Desktop\ysm\swap_textures.py`

## 环境
- MC 1.21.1 + NeoForge 21.1.252 + JDK 21
- Python `C:\Users\admin\AppData\Local\Python\pythoncore-3.14-64\python.exe`
- 测试服 25599 世界 `run/server/world` 每次删掉重建

## 高度分带（四带结构 用户确认）
| 高度 | 层 | 实现 |
|---|---|---|
| -64 ~ 0 | 地下碎岛 | underground_island.json 高频噪声 + squeeze |
| 0 ~ 128 | 大陆实心 | mainland.json 自造 不用 sloped_cheese |
| 128 ~ 192 | 空置域 | void_band.json mask 归零 |
| 192 ~ 256 | 空岛 | **code 生成 Feature** 不是密度函数 |

final_density = max(island_density, sky_island_density) * void_band

## 关键坑
- **`default_fluid` 必须是 air** 留默认 water + sea_level 62 会灌满所有洞穴 看起来像实体
- **`ore_veins_enabled` 必须 false** 否则生成 granite andesite 杂石
- 1.21.1 密度函数字段名是 `argument` 不是 `input`（squeeze/interpolated/abs/blend_density 都一样）
- `squeeze` 值域 `(x/2)^3*3` x=1 时只有 0.375 不是 1
- `vertical_gradient true_at_and_below absolute:0` 会填满所有 y<=0
- `gradle build` 对纯资源改动可能报 UP-TO-DATE 要验证 jar 内容
- **`neoforge:remove_features` 引用的 feature ID 必须真实存在**，引用不存在的 ID 会在注册表加载期抛 `Unbound values` 整个存档起不来
  （踩过 `twilightforest:thorn_rose`、`minecraft:*_blob` 都不存在）
- **绝对不要 `Get-Process java | Stop-Process`** 会杀 gradle daemon 卡死
  只杀 MC 服务端：`Get-Process java | Where-Object { (Get-CimInstance Win32_Process -Filter "ProcessId=$($_.Id)").CommandLine -match 'net.neoforged' } | Stop-Process -Force`
- PS 5.1 里长 heredoc 会崩 PSReadLine 改用**独立 .py 脚本文件**
- `git commit -m "中文"` 乱码 用 `[IO.File]::WriteAllText(路径,内容,[Text.UTF8Encoding]::new($false))` + `git commit -F`
- 1.21.1 原版资源可从 `C:\Users\admin\.gradle\caches\fabric-loom\1.21.1\net.neoforged.neoforge_21.1.235\minecraft-merged-game-resources.jar` 提取

## 空岛 Feature（SkyIslandFeature）
- `spacing` 单位是区块 500 格 = 31
- 用户要求：500 格 1 个 直径约 70（半径 26~35）上平下尖
- `placed_feature` 的 count / in_square / height_range / biome
- 通过 `neoforge:biome_modifier` 的 `add_features` 挂到群系

## 用户偏好（重要）
- **写完诊断代码必须删掉再提交**
- 忘事时去 `/memories/repo/` 和本文件查
- 允许在工作区写记忆文件
- 用户会频繁给截图反馈 先看图再动手
- 贴图先用原版占位 美术后面替换

## 待办
- 落点只找露天地表（已完成 hasOpenSky）
- 暮色群系里的荆棘 brown_thorns/green_thorns 要去掉（用 neoforge:remove_features）
- 天光 night=11 center=13 day=15
- y290 出现空岛方块原因未明
- 截图 12.29 显示天光 0 光照钳制可能没生效 待查
