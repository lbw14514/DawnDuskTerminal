package com.dawnduskterminal.client.animation;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

/**
 * F3+T 与资源包切换时把模型包缓存清掉
 *
 * <p>几何与动画都按文件缓存，而资源重载之后同一路径可能已经是另一份内容；不清理就会一直用旧模型。
 * 清理是整块作废而不是逐个比对：重载本身很慢，重新解析几 MB 的动画在人类感知里是瞬时的。
 *
 * <p>渲染器里缓存的 native 网格不做处理：它按模型对象身份比对，重载后对象变了自然重建，
 * 旧句柄由 {@code NativeGeoMesh} 的 Cleaner 回收。
 */
public final class YsmEntityReload extends SimplePreparableReloadListener<Void> {
    private YsmEntityReload() {
    }

    public static void register(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new YsmEntityReload());
    }

    @Override
    protected Void prepare(ResourceManager manager, ProfilerFiller profiler) {
        return null;
    }

    @Override
    protected void apply(Void unused, ResourceManager manager, ProfilerFiller profiler) {
        YsmPacks.invalidate();
        AnimatedStripTexture.invalidate();
    }
}
