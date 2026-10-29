# 配置项

配置项定义在 `config/DdtConfig` 中，类型为 NeoForge 的 COMMON。

配置文件为游戏目录下的 `config/dawnduskterminal-common.toml`。修改配置后需要重启游戏方可生效。

每个取值方法均设有回退默认值：配置尚未加载时返回该默认值，而不抛出异常。

## lighting

- `sun_angle_max`：太阳最大高度角，单位为度，默认值 20.0，取值范围为 0 至 90；其取值方法在配置缺失时回退为 20.0。该配置项同时影响服务端的 `ChronoLine` 与客户端天空。客户端天空按 `0.25 - param * sun_angle_max / 360` 换算为原版的 `timeOfDay`，参见 `mixin/ClientLevelTimeOfDayMixin`。
- `sky_light_by_distance`：默认值为 true。取值为 true 时，天光依据坐标沿晨昏线两侧衰减；取值为 false 时，该维度内的天光恒为 15。
- `sky_light_day`：昼侧最远处的天光，默认值 15，取值范围为 0 至 15。
- `sky_light_center`：晨昏线上的天光，默认值 14，取值范围为 0 至 15。
- `sky_light_night`：夜侧最远处的天光，默认值 12，取值范围为 0 至 15。

配置注释中已注明：取值在 8 及以下时会导致怪物生成。

## chrono_line

- `max_distance`：即 `ChronoLine.param` 的除数，同时决定坐标折算至最大太阳角所需的距离。默认值 5000.0，取值范围为 16 至 1000000。
- `normal_override`：默认值为空字符串。填写 `x,z` 可固定晨昏线法向，非法输入将回退为随机取值。取空字符串表示每个世界在创建时随机确定法向：以朝东为基准，抖动幅度为正负 30 度，随后再随机决定是否翻转至朝西。

## portal

- `cooldown_ticks`：默认值 100，取值范围为 0 至 72000。
- `entity_debuff`：默认值 true。

说明：传送改为即时执行之后，上述两个配置项已无读取方，属于遗留配置，保留仅为兼容既有配置文件。

## compat

- `tolerate_feature_cycles`：默认值 true。取值为 true 时，以可容忍特征环的排序实现替换原版的 `FeatureSorter`。

在同一维度内混用原版群系与模组群系时必须开启该配置项，调色板维度正属于这种情况。

## render

- `native_mesh`：默认值 true，用于指定高塔恶魂的渲染是否使用 jar 内自带的 native 网格内核。取值为 false 时改用 GeckoLib 的 Java 顶点变换，画面效果一致，代价是每帧多出约两万个临时对象。

若怀疑崩溃由 native 引起，应先关闭该配置项并重启游戏。

对应的系统属性为 `-Ddawnduskterminal.disableNative=true`，环境变量为 `DAWNDUSKTERMINAL_DISABLE_NATIVE=true`。

native 在加载成功时会于 `config/dawnduskterminal-native.crash-marker` 写入标记，进程正常退出时自动删除该标记。若上一次进程发生硬崩溃，标记将被保留，下一次启动时据此自动禁用 native；确认运行稳定后删除该文件即可恢复启用。

`-Ddawnduskterminal.forceNative=true` 可在单次启动中忽略该标记。
