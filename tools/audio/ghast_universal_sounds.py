"""把恶魂模型包里那批音频转成 Minecraft 能播的 Ogg Vorbis

源目录里两批文件的容器并不一致：`*.ogg.wav` 是 40 kHz 单声道 PCM WAV（扩展名是错的，
内容按 RIFF 头认定为 WAVE），`charge.ogg` 是真正的 Ogg Vorbis。Minecraft 只播 Vorbis，
所以前者必须重新编码，后者原样复制即可。

输出保留源文件名，只去掉那个多余的 `.wav`：这批音频是「恶魂通用」素材，
`moan1`、`scream1`、`death`、`charge`、`fireball4`、`affectionate_scream` 这些名字
以后还要被其它恶魂变体共用，不能按某一只生物改名。目录用 `ghast_universal`
（源目录名 `ghast_unversal` 是拼写笔误，动画文件本身叫 `ghast_universal.animation.json`）。

用法：

    python ghast_universal_sounds.py <源目录> <资源目录>

`<资源目录>` 指 `src/main/resources/assets/dawnduskterminal/sounds`。
"""

from __future__ import annotations

import shutil
import sys
from pathlib import Path

import soundfile as sf

TARGET_DIR = "ghast_universal"


def target_name(source: Path) -> str:
    """去掉末尾的 `.wav`（`moan1.ogg.wav` -> `moan1.ogg`），其余名字原样保留"""
    name = source.name
    while name.lower().endswith(".wav"):
        name = name[: -len(".wav")]
    if not name.lower().endswith(".ogg"):
        name += ".ogg"
    return name


def convert(source: Path, target: Path) -> None:
    info = sf.info(str(source))
    if info.format == "OGG" and info.subtype == "VORBIS":
        # 已经是 Vorbis，重新编码只会再掉一代质量
        shutil.copyfile(source, target)
        print(f"copy  {target.name:26s} {info.samplerate:6d} Hz {info.duration:5.2f}s "
              f"{target.stat().st_size:7d} B  (already Vorbis)")
        return

    data, samplerate = sf.read(str(source), dtype="int16", always_2d=False)
    sf.write(str(target), data, samplerate, format="OGG", subtype="VORBIS")
    print(f"enc   {target.name:26s} {samplerate:6d} Hz {info.duration:5.2f}s "
          f"{target.stat().st_size:7d} B")


def main(argv: list[str]) -> int:
    if len(argv) != 3:
        print(__doc__)
        return 2
    source_dir = Path(argv[1])
    sounds_dir = Path(argv[2]) / TARGET_DIR
    sounds_dir.mkdir(parents=True, exist_ok=True)
    for source in sorted(source_dir.iterdir()):
        # 源目录里混着 Windows 生成的 desktop.ini，按扩展名筛掉
        if not source.is_file() or source.suffix.lower() not in {".ogg", ".wav"}:
            continue
        convert(source, sounds_dir / target_name(source))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
