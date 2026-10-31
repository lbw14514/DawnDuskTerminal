"""从「源 YSM 动画」与「转换后动画」的差异生成运行期损失清单

转换器（`ysm_to_geckolib.py`）把 YSM 特有的构造改写成 GeckoLib 能编译的形式，其中三级构造
（`ysm.bone_rot`、`ysm.perlin_noise`、`ysm.particle` 等）没有等价物，一律改写成 `0`。改写本身是
静默的：文件仍然能加载、片段仍然能播放，只是那些通道恒为 0。谁去补回这些通道、补回的表达式是
什么，此前只写在 `--report` 的输出里，由人读。

本脚本把这份信息变成一份随模型走的清单（`assets/<mod>/ysm_runtime/<model>.json`），
运行期由 `client/YsmRuntimeContract` 读取并在首次渲染时自检：某个通道在源里有动态值、
转换后归零、而本帧没有代码写回它，就打一条警告。这样「换了新模型忘了接物理层」会在游戏里
报出来，而不是表现为「动画有点僵」。

判定规则（逐骨骼逐轴，只针对实体控制器确实播放的片段）：

  源表达式含字母（query / 变量 / 函数调用） 且 转换后的同一通道不含字母  -> 记为待补回
  片段在源里存在、但转换后的文件里没有 -> 必须由 `--not-played` 显式声明，否则脚本报错退出

第二条是这份清单的主要价值：片段被转换器整段丢掉是最容易被忽略的一种损失
（`ghast_elite` 的 `parallel1` 即属此类），显式声明之后它才不会在下次迁移时悄悄消失。

用法：
    python runtime_contract.py <source.animation.json> <converted.animation.json> \\
        --model ghast_elite --played parallel0 --not-played parallel1 \\
        --out ../../src/main/resources/assets/dawnduskterminal/ysm_runtime/ghast_elite.json
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

# 表达式里出现这些字符就说明它不是纯数字常量，即「由 query、变量或函数调用驱动」
LETTERS = set("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ_")

CHANNELS = ("rotation", "position", "scale")
AXES = ("x", "y", "z")


def is_dynamic(expr) -> bool:
    """表达式里是否含字母（= 不是纯数值常量）"""
    return isinstance(expr, str) and any(char in LETTERS for char in expr)


def axes_of(channel_value) -> dict[str, object]:
    """把一个通道写成 {x: 表达式, y: 表达式, z: 表达式}，同时接受数组与字典两种写法"""
    if isinstance(channel_value, dict):
        return {axis: channel_value.get(axis, 0) for axis in AXES}
    if isinstance(channel_value, list) and len(channel_value) == 3:
        return dict(zip(AXES, channel_value))
    raise ValueError(f"无法识别的通道写法：{channel_value!r}")


def collect(source: dict, converted: dict, played: list[str], not_played: list[str]) -> dict:
    source_clips = source.get("animations", {})
    converted_clips = converted.get("animations", {})
    unaccounted = sorted(set(source_clips) - set(converted_clips) - set(not_played))
    if unaccounted:
        raise SystemExit(
            "转换后的文件里缺少这些片段，但也没有用 --not-played 声明：\n  "
            + "\n  ".join(unaccounted)
        )

    channels = []
    for clip in played:
        if clip not in source_clips:
            raise SystemExit(f"--played 指定的片段在源文件里不存在：{clip}")
        if clip not in converted_clips:
            raise SystemExit(f"--played 指定的片段在转换后的文件里不存在：{clip}")
        source_bones = source_clips[clip].get("bones", {})
        converted_bones = converted_clips[clip].get("bones", {})
        for bone, source_bone in source_bones.items():
            converted_bone = converted_bones.get(bone, {})
            for channel in CHANNELS:
                if channel not in source_bone:
                    continue
                source_axes = axes_of(source_bone[channel])
                if channel not in converted_bone:
                    converted_axes = {axis: 0 for axis in AXES}
                else:
                    converted_axes = axes_of(converted_bone[channel])
                for index, axis in enumerate(AXES):
                    if not is_dynamic(source_axes[axis]):
                        continue
                    if is_dynamic(converted_axes[axis]):
                        continue
                    channels.append({
                        "clip": clip,
                        "bone": bone,
                        "channel": channel,
                        "axis": index,
                        "expression": source_axes[axis],
                        "converted": converted_axes[axis],
                    })
    return {
        "played_clips": list(played),
        "skipped_clips": {clip: "片段未播放，其动态通道不在运行期职责内" for clip in not_played},
        "channels": channels,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="生成 YSM 运行期损失清单")
    parser.add_argument("source", type=Path)
    parser.add_argument("converted", type=Path)
    parser.add_argument("--model", required=True)
    parser.add_argument("--played", nargs="*", default=[], help="实体控制器实际播放的片段")
    parser.add_argument("--not-played", nargs="*", default=[], help="有意不播放的片段")
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()

    with args.source.open(encoding="utf-8") as handle:
        source = json.load(handle)
    with args.converted.open(encoding="utf-8") as handle:
        converted = json.load(handle)

    contract = collect(source, converted, args.played, args.not_played)
    document = {
        "model": args.model,
        "source": args.source.name,
        "converted": args.converted.name,
        **contract,
    }
    args.out.parent.mkdir(parents=True, exist_ok=True)
    with args.out.open("w", encoding="utf-8") as handle:
        json.dump(document, handle, ensure_ascii=False, indent=2)
        handle.write("\n")

    print(f"待运行期补回：{len(document['channels'])} 条通道")
    for entry in document["channels"]:
        print(f"  {entry['clip']}/{entry['bone']}.{entry['channel']}[{entry['axis']}]"
              f"  <- {entry['expression']}  (转换后：{entry['converted']})")
    for clip in document["skipped_clips"]:
        print(f"未播放：{clip}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
