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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ChainedJsonException;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

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
    /** YSM 的动画文件命名后缀，列目录时要一并去掉 */
    private static final String ANIMATION_SUFFIX = ".animation";

    private static final Map<ResourceLocation, YsmPack> PACKS = new HashMap<>();

    private YsmPacks() {}

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

        String defaultTexture = textures.containsKey("ghast")
                ? "ghast"
                : textures.keySet().stream().sorted().findFirst().orElse("");
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
        if (animations.containsKey("main") && !"main".equals(nameOf(animations.get("main")))) {
            DawnDuskTerminal.LOGGER.error("YSM 模型包 {} 的主动画文件解析异常：{}", id, animations.get("main"));
        }
        YsmPack pack = new YsmPack(
                id,
                models,
                animations,
                textures,
                defaultTexture,
                heightScale,
                widthScale,
                extraAnimations(animations, json),
                roamingOptions(json));
        mergeAnimationFiles(pack);
        if (models.isEmpty()) {
            DawnDuskTerminal.LOGGER.error(
                    "YSM 模型包 {} 里没有 models/*.json（目录 assets/{}/{}），实体将无法渲染", id, id.getNamespace(), root);
        }
        if (textures.isEmpty()) {
            DawnDuskTerminal.LOGGER.error(
                    "YSM 模型包 {} 里没有 textures/*.png（目录 assets/{}/{}）", id, id.getNamespace(), root);
        }
        return pack;
    }

    /**
     * {@code properties.extra_animation}：按钮键 → 片段名
     *
     * <p>YSM 的写法里值是<strong>显示名</strong>（{@code id1} 对应「声音1」），片段名一般就等于键；
     * 但也有键与片段不同的（{@code statue} 播 {@code pose}）。所以逐个按「键优先、其次值」去动画文件里找，
     * 找不到就跳过；值以 {@code #} 开头的是配置表单（{@code extra0}），不是动作。
     *
     * <p>这里只保留键与声明值，真正的片段名在 {@link YsmPack#extraClip} 里按合并后的动画文件解析。
     */
    private static Map<String, String> extraAnimations(Map<String, ResourceLocation> animations, JsonObject json) {
        Map<String, String> result = new LinkedHashMap<>();
        if (json == null || !json.has("properties")) {
            return result;
        }
        JsonObject properties = json.getAsJsonObject("properties");
        if (!properties.has("extra_animation")) {
            return result;
        }
        JsonObject declared = properties.getAsJsonObject("extra_animation");
        for (Map.Entry<String, JsonElement> entry : declared.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue().isJsonPrimitive() ? entry.getValue().getAsString() : "";
            if (value.startsWith("#")) {
                continue;
            }
            result.put(key, value);
        }
        return result;
    }

    /**
     * {@code properties.extra_animation_buttons[].config_forms}：模型包声明的外观开关
     *
     * <p>每一条是「一个 Molang 变量 + 类型 + 取值范围」，例如 {@code v.roaming.hat}（帽子）、
     * {@code v.roaming.type}（形态 0..2）、{@code v.cloth}（换衣 0..2）。宿主的职责是把这些变量写进
     * Molang 存储，模型包的 {@code pre_parallel2} 用它们决定哪些部件缩放为 0。
     *
     * <p>不预置默认值：YSM 里未初始化的变量就是 0，和「帽子关、形态 0、贴图 0」的直觉一致。
     */
    private static List<YsmPack.RoamingOption> roamingOptions(JsonObject json) {
        List<YsmPack.RoamingOption> options = new ArrayList<>();
        if (json == null || !json.has("properties")) {
            return options;
        }
        JsonObject properties = json.getAsJsonObject("properties");
        if (!properties.has("extra_animation_buttons")) {
            return options;
        }
        for (JsonElement element : properties.getAsJsonArray("extra_animation_buttons")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject button = element.getAsJsonObject();
            if (!button.has("config_forms")) {
                continue;
            }
            for (JsonElement form : button.getAsJsonArray("config_forms")) {
                if (!form.isJsonObject()) {
                    continue;
                }
                JsonObject option = form.getAsJsonObject();
                if (!option.has("value")) {
                    continue;
                }
                String variable = option.get("value").getAsString();
                if (!variable.startsWith("v.")) {
                    continue;
                }
                String type = option.has("type") ? option.get("type").getAsString() : "range";
                double min = option.has("min") ? option.get("min").getAsDouble() : 0.0D;
                double max = option.has("max") ? option.get("max").getAsDouble() : 1.0D;
                double step = option.has("step") ? option.get("step").getAsDouble() : 1.0D;
                String title = option.has("title") ? option.get("title").getAsString() : variable;
                options.add(new YsmPack.RoamingOption(variable.substring(2), type, title, min, max, step));
            }
        }
        return options;
    }

    /**
     * 把模型包的动画文件合并成一份，挂在主文件的位置上
     *
     * <p>YSM 的动画分散在多个文件里：{@code main} 是主状态机，{@code extra} 是手动动作
     * （舞蹈、待机语音动作），{@code tlm}/{@code tac} 是兼容层。宿主的控制器只认一个动画文件，
     * 因此这里把 {@code main} 与 {@code extra} 合并后写回主文件的位置，控制器就能按片段名找到它们。
     *
     * <p>只合并这两个：其余文件是给别的模组用的兼容层，加起来超过 2 MB，而本模组一条都不用。
     */
    private static void mergeAnimationFiles(YsmPack pack) {
        ResourceLocation main = pack.animation();
        if (main == null) {
            return;
        }
        AnimationFile merged = new AnimationFile();
        merge(merged, animations(main));
        ResourceLocation extra = pack.animations().get("extra");
        if (extra != null) {
            merge(merged, animations(extra));
        }
        GeckoLibCache.getInstance().getAnimations().put(main, merged);
    }

    private static String nameOf(ResourceLocation file) {
        String path = file.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static void merge(AnimationFile target, AnimationFile source) {
        source.animations().forEach((name, animation) -> {
            if (target.getAnimation(name) == null) {
                target.putAnimation(name, animation);
            }
        });
    }

    /**
     * 文件路径 → 包内键名：去掉目录前缀、去掉扩展名、再去掉 YSM 动画文件的 {@code .animation} 段
     *
     * <p>抽成静态方法是为了能直接测，见 {@code gametest/YsmPackResourceTests}。
     */
    public static String nameFor(String dir, String path, String suffix) {
        String name = path.substring(dir.length() + 1, path.length() - suffix.length());
        if (name.endsWith(ANIMATION_SUFFIX)) {
            name = name.substring(0, name.length() - ANIMATION_SUFFIX.length());
        }
        return name;
    }

    /**
     * 列出一个子目录下的文件，键是文件名去掉扩展名
     *
     * <p>动画文件按 YSM 的约定叫 {@code main.animation.json}，键要写成 {@code main}（不是
     * {@code main.animation}），否则 {@link YsmPack#animation()} 找不到主文件、
     * 退回到排序第一的那一个——{@code extra.animation.json} 正好排在 {@code main} 前面，
     * 结果是主状态机与并行层全部落空（实测症状：所有形态叠加显示、走与站都没有动作）。
     */
    private static Map<String, ResourceLocation> list(String namespace, String dir, String suffix) {
        ResourceManager manager = Minecraft.getInstance().getResourceManager();
        Map<String, ResourceLocation> found = new LinkedHashMap<>();
        for (ResourceLocation file : manager.listResources(
                        dir,
                        location -> location.getNamespace().equals(namespace)
                                && location.getPath().endsWith(suffix))
                .keySet()) {
            String path = file.getPath();
            found.put(nameFor(dir, path, suffix), file);
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
                DawnDuskTerminal.LOGGER.error("几何文件版本 {} 不被支持，请联系模型作者：{}", raw.getFormatVersion(), file);
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
