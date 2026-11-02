package com.dawnduskterminal.client.animation;

import com.dawnduskterminal.DawnDuskTerminal;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * 贴图动画条：把 {@code .mcmeta} 描述的一帧单独上传成一张贴图
 *
 * <p>原版只给图集（atlas）里的贴图做帧动画，实体直接按 {@link ResourceLocation} 绑定贴图时不会。
 * 而弦用的两张贴图是竖条（{@code 16x512} 与 {@code 32x1024}，各 32 帧），几何里声明的
 * {@code texture_height} 只有一帧高，于是 UV 的 0..1 会横跨整条竖条——画出来就是把 32 帧压成一张，
 * 看着「贴图不对」。
 *
 * <p>这里为每条竖条注册一张**一帧大小**的动态贴图，逐帧把对应那一段拷进去再上传。
 * 渲染类型因此固定绑这张动态贴图，UV 的 0..1 正好覆盖一帧，与几何的声明一致。
 *
 * <p>只在渲染线程使用；竖条原图缓存住（最多 32 KB），资源重载后由 {@link #invalidate()} 清掉。
 */
public final class AnimatedStripTexture {
    private static final Map<ResourceLocation, Strip> STRIPS = new HashMap<>();

    private AnimatedStripTexture() {}

    /**
     * 取这条贴图当前帧对应的动态贴图位置，并推进到当前帧
     *
     * <p>读不到图或没有 {@code .mcmeta} 时原样返回传入的位置，即回退成静态贴图。
     */
    public static ResourceLocation frameOf(ResourceLocation strip) {
        Strip state = STRIPS.computeIfAbsent(strip, Strip::load);
        if (state == null) {
            return strip;
        }
        state.advance();
        return state.frameId;
    }

    /** 资源重载：丢掉所有竖条，下次用到时重新读 */
    public static void invalidate() {
        STRIPS.clear();
    }

    private static final class Strip {
        private final NativeImage source;
        private final int frameSize;
        private final int frameCount;
        private final int frameTicks;
        private final NativeImage target;
        private final DynamicTexture dynamic;
        private final ResourceLocation frameId;

        private int shownFrame = -1;

        private Strip(
                NativeImage source,
                int frameSize,
                int frameCount,
                int frameTicks,
                ResourceLocation frameId,
                DynamicTexture dynamic) {
            this.source = source;
            this.frameSize = frameSize;
            this.frameCount = frameCount;
            this.frameTicks = frameTicks;
            this.target = dynamic.getPixels();
            this.dynamic = dynamic;
            this.frameId = frameId;
        }

        private static Strip load(ResourceLocation strip) {
            try (InputStream stream = Minecraft.getInstance()
                    .getResourceManager()
                    .getResourceOrThrow(strip)
                    .open()) {
                NativeImage source = NativeImage.read(stream);
                int frameSize = source.getWidth();
                // 竖条：宽度就是一帧的边长，高度除以宽度即帧数
                int frameCount = Math.max(1, source.getHeight() / frameSize);
                if (frameCount == 1) {
                    source.close();
                    return null;
                }
                int frameTicks = readFrameTicks(strip, frameCount);
                NativeImage pixels = new NativeImage(frameSize, frameSize, false);
                DynamicTexture dynamic = new DynamicTexture(pixels);
                ResourceLocation frameId =
                        ResourceLocation.fromNamespaceAndPath(strip.getNamespace(), "dynamic/" + strip.getPath());
                Minecraft.getInstance().getTextureManager().register(frameId, dynamic);
                return new Strip(source, frameSize, frameCount, frameTicks, frameId, dynamic);
            } catch (Exception error) {
                DawnDuskTerminal.LOGGER.warn("贴图动画条读取失败：{}", strip, error);
                return null;
            }
        }

        /** {@code .mcmeta} 的 {@code animation.frametime}，单位 tick；缺失时按 1 处理 */
        private static int readFrameTicks(ResourceLocation strip, int frameCount) {
            ResourceLocation meta =
                    ResourceLocation.fromNamespaceAndPath(strip.getNamespace(), strip.getPath() + ".mcmeta");
            try (InputStream stream = Minecraft.getInstance()
                    .getResourceManager()
                    .getResourceOrThrow(meta)
                    .open()) {
                JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                if (json.has("animation") && json.getAsJsonObject("animation").has("frametime")) {
                    return Math.max(
                            1,
                            json.getAsJsonObject("animation").get("frametime").getAsInt());
                }
            } catch (Exception ignored) {
                // 没有 mcmeta 或读不动时按每 tick 一帧，与不写 frametime 的原版贴图一致
            }
            return 1;
        }

        private void advance() {
            long ticks = Util.getMillis() / 50L;
            int frame = (int) ((ticks / this.frameTicks) % this.frameCount);
            if (frame == this.shownFrame) {
                return;
            }
            this.shownFrame = frame;
            int offsetY = frame * this.frameSize;
            for (int y = 0; y < this.frameSize; y++) {
                for (int x = 0; x < this.frameSize; x++) {
                    this.target.setPixelRGBA(x, y, this.source.getPixelRGBA(x, offsetY + y));
                }
            }
            this.dynamic.upload();
        }
    }
}
