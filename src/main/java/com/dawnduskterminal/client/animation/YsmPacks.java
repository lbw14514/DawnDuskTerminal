package com.dawnduskterminal.client.animation;

import com.dawnduskterminal.DawnDuskTerminal;
import com.dawnduskterminal.geckolib3.core.builder.Animation;
import com.dawnduskterminal.geckolib3.core.molang.MolangParser;
import com.dawnduskterminal.geckolib3.file.AnimationFile;
import com.dawnduskterminal.geckolib3.geo.raw.pojo.Converter;
import com.dawnduskterminal.geckolib3.geo.raw.pojo.FormatVersion;
import com.dawnduskterminal.geckolib3.geo.raw.pojo.RawGeoModel;
import com.dawnduskterminal.geckolib3.geo.raw.tree.RawGeometryTree;
import com.dawnduskterminal.geckolib3.geo.render.GeoBuilder;
import com.dawnduskterminal.geckolib3.geo.render.built.GeoModel;
import com.dawnduskterminal.geckolib3.resource.GeckoLibCache;
import com.dawnduskterminal.geckolib3.util.json.JsonAnimationUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ChainedJsonException;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * YSM 模型包：磁盘上的目录布局与解析结果的读取口
 *
 * <p>包按 YSM 的原样布局放在 {@code assets/<namespace>/ysm/<包名>/} 下，一个文件都不改写：
 *
 * <pre>
 * ysm.json                    包元数据，{@code properties.height_scale} 等
 * models/main.json            Bedrock 几何
 * animations/main.animation.json   Molang 表达式写法的动画
 * textures/&lt;名字&gt;.png       贴图，默认贴图由 {@code properties.default_texture} 指定
 * controller/*.json           并行动画控制器（分层关系的原始依据）
 * </pre>
 *
 * <p>几何与动画按文件懒加载并缓存在 {@link GeckoLibCache} 里：表达式解析不便宜，
 * 而这些文件在资源重载之前不会变。
 */
public final class YsmPacks {
    /** 模型包在 jar 里的根目录，一个包就是这下面的一个文件夹 */
    private static final String PACK_ROOT = "ysm/";
    private static final Map<ResourceLocation, YsmPack> PACKS = new HashMap<>();

    private YsmPacks() {
    }

    public static YsmPack get(ResourceLocation id) {
        return PACKS.computeIfAbsent(id, YsmPacks::load);
    }

    /** 资源重载：包、几何、动画全部作废 */
    public static void invalidate() {
        PACKS.clear();
        GeckoLibCache.getInstance().invalidate();
    }

    private static YsmPack load(ResourceLocation id) {
        // id 是包名（例如 dawnduskterminal:tower_ghast），目录前缀在这里拼，id 里不要再带 ysm/
        String root = PACK_ROOT + id.getPath();
        Map<String, ResourceLocation> models = list(id.getNamespace(), root + "/models", ".json");
        Map<String, ResourceLocation> animations = list(id.getNamespace(), root + "/animations", ".json");
        Map<String, ResourceLocation> textures = list(id.getNamespace(), root + "/textures", ".png");

        String defaultTexture = textures.containsKey("ghast") ? "ghast" : textures.keySet().stream().sorted().findFirst().orElse("");
        float heightScale = 1.0F;
        float widthScale = 1.0F;
        ResourceLocation meta = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), root + "/ysm.json");
        JsonObject json = readJson(meta);
        if (json != null && json.has("properties")) {
            JsonObject properties = json.getAsJsonObject("properties");
            if (properties.has("height_scale")) {
                heightScale = properties.get("height_scale").getAsFloat();
            }
            if (properties.has("width_scale")) {
                widthScale = properties.get("width_scale").getAsFloat();
            }
            if (properties.has("default_texture")) {
                String declared = properties.get("default_texture").getAsString();
                if (textures.containsKey(declared)) {
                    defaultTexture = declared;
                }
            }
        }
        if (models.isEmpty()) {
            DawnDuskTerminal.LOGGER.error("YSM 模型包 {} 里没有 models/*.json（目录 assets/{}/{}），实体将无法渲染", id, id.getNamespace(), root);
        }
        if (textures.isEmpty()) {
            DawnDuskTerminal.LOGGER.error("YSM 模型包 {} 里没有 textures/*.png（目录 assets/{}/{}）", id, id.getNamespace(), root);
        }
        return new YsmPack(id, models, animations, textures, defaultTexture, heightScale, widthScale);
    }

    private static Map<String, ResourceLocation> list(String namespace, String dir, String suffix) {
        ResourceManager manager = Minecraft.getInstance().getResourceManager();
        Map<String, ResourceLocation> found = new LinkedHashMap<>();
        for (ResourceLocation file : manager.listResources(dir,
            location -> location.getNamespace().equals(namespace) && location.getPath().endsWith(suffix)).keySet()) {
            String path = file.getPath();
            found.put(path.substring(dir.length() + 1, path.length() - suffix.length()), file);
        }
        return found;
    }

    /** 几何，按文件缓存；解析失败返回 null */
    public static GeoModel geometry(ResourceLocation file) {
        GeoModel cached = GeckoLibCache.getInstance().getGeoModels().get(file);
        if (cached != null) {
            return cached;
        }
        try (InputStream stream = open(file)) {
            RawGeoModel raw = Converter.fromInputStream(stream);
            if (raw == null || raw.getFormatVersion() == null || raw.getMinecraftGeometry() == null) {
                DawnDuskTerminal.LOGGER.error("几何文件格式无法识别：{}", file);
                return null;
            }
            if (raw.getFormatVersion() != FormatVersion.NEW) {
                DawnDuskTerminal.LOGGER.error("几何文件版本 {} 不被支持，请联系模型作者：{}",
                    raw.getFormatVersion(), file);
                return null;
            }
            GeoModel model = GeoBuilder.getGeoBuilder().constructGeoModel(RawGeometryTree.parseHierarchy(raw));
            GeckoLibCache.getInstance().getGeoModels().put(file, model);
            return model;
        } catch (IOException | RuntimeException error) {
            DawnDuskTerminal.LOGGER.error("几何文件读取失败：{}", file, error);
            return null;
        }
    }

    /** 动画，按文件缓存；单条片段解析失败只丢掉那一条 */
    public static AnimationFile animations(ResourceLocation file) {
        AnimationFile cached = GeckoLibCache.getInstance().getAnimations().get(file);
        if (cached != null) {
            return cached;
        }
        AnimationFile result = new AnimationFile();
        JsonObject json = readJson(file);
        if (json != null) {
            MolangParser parser = GeckoLibCache.getInstance().parser;
            for (Map.Entry<String, JsonElement> entry : JsonAnimationUtils.getAnimations(json)) {
                String name = entry.getKey();
                try {
                    Animation animation = JsonAnimationUtils.deserializeJsonToAnimation(
                        JsonAnimationUtils.getAnimation(json, name), parser);
                    if (animation != null) {
                        result.putAnimation(name, animation);
                    }
                } catch (ChainedJsonException | RuntimeException error) {
                    DawnDuskTerminal.LOGGER.error("片段 {} 解析失败（{}）：{}", name, file, error.getMessage());
                }
            }
        }
        GeckoLibCache.getInstance().getAnimations().put(file, result);
        return result;
    }

    private static JsonObject readJson(ResourceLocation file) {
        try (Reader reader = new InputStreamReader(open(file), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException | RuntimeException error) {
            DawnDuskTerminal.LOGGER.error("JSON 读取失败：{}", file, error);
            return null;
        }
    }

    private static InputStream open(ResourceLocation file) throws IOException {
        Resource resource = Minecraft.getInstance().getResourceManager().getResourceOrThrow(file);
        return resource.open();
    }
}
