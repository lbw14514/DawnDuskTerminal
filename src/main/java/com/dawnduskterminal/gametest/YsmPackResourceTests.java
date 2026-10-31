package com.dawnduskterminal.gametest;

import com.dawnduskterminal.geckolib3.geo.raw.pojo.Converter;
import com.dawnduskterminal.geckolib3.geo.raw.pojo.RawGeoModel;
import com.dawnduskterminal.geckolib3.geo.raw.tree.RawGeometryTree;
import com.dawnduskterminal.geckolib3.geo.render.GeoBuilder;
import com.dawnduskterminal.geckolib3.geo.render.built.GeoBone;
import com.dawnduskterminal.geckolib3.geo.render.built.GeoModel;
import com.dawnduskterminal.geckolib3.util.json.JsonAnimationUtils;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 模型包在 jar 里齐不齐、能不能被解析
 *
 * <p>模型加载失败是静默的：几何文件缺失只会让实体停在初始姿势，动画文件缺一条片段只会让某个通道不动，
 * 两者都不会抛异常。所以这里在服务端把两个模型的声明文件读一遍，至少保证「文件在、JSON 能解析、片段数与预期一致」。
 *
 * <p>文件直接从 classpath 读（{@code /assets/dawnduskterminal/ysm/...}），不走服务端的
 * {@code ResourceManager}：后者只提供 {@code data/}，{@code assets/} 在专用服务端上根本不可见。
 *
 * <p>只做结构化检查，不求值表达式：Molang 求值要客户端的绑定表，专用服务端上不该加载它们。
 * 表达式能不能求对只能在客户端里看，见 `docs/ysm.md`。
 */
@GameTestHolder("dawnduskterminal")
@PrefixGameTestTemplate(false)
public final class YsmPackResourceTests {
    @GameTest(template = "ddt_empty")
    public static void packsAreComplete(GameTestHelper helper) {
        check(helper, "tower_ghast", 50);
        check(helper, "ghast_elite", 2);
        helper.succeed();
    }

    private static void check(GameTestHelper helper, String pack, int expectedClips) {
        String geo = packFile(pack, "models/main.json");
        String animations = packFile(pack, "animations/main.animation.json");
        for (String required : new String[] {geo, animations, packFile(pack, "textures/ghast.png"),
            packFile(pack, "ysm.json")}) {
            helper.assertTrue(open(required) != null, "缺少模型包文件 " + required);
        }

        int cubes = 0;
        try (InputStream stream = open(geo)) {
            RawGeoModel raw = Converter.fromInputStream(stream);
            helper.assertTrue(raw != null && raw.getMinecraftGeometry() != null, "几何文件为空 " + geo);
            GeoModel model = GeoBuilder.getGeoBuilder().constructGeoModel(RawGeometryTree.parseHierarchy(raw));
            helper.assertTrue(!model.topLevelBones().isEmpty(), "几何文件里没有骨骼 " + geo);
            for (GeoBone bone : model.topLevelBones()) {
                cubes += countCubes(bone);
            }
        } catch (IOException | RuntimeException error) {
            helper.assertTrue(false, "几何文件读取失败 " + geo + "：" + error);
        }
        helper.assertTrue(cubes > 0, "几何文件里没有可渲染的立方体 " + geo);

        try (InputStream stream = open(animations)) {
            JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                .getAsJsonObject();
            int clips = JsonAnimationUtils.getAnimations(json).size();
            helper.assertTrue(clips == expectedClips,
                pack + " 的片段数应为 " + expectedClips + "，实际 " + clips);
        } catch (IOException | RuntimeException error) {
            helper.assertTrue(false, "动画文件读取失败 " + animations + "：" + error);
        }
    }

    private static InputStream open(String path) {
        return YsmPackResourceTests.class.getResourceAsStream(path);
    }

    private static int countCubes(GeoBone bone) {
        int cubes = bone.cubes() == null ? 0 : bone.cubes().getCubeCount();
        for (GeoBone child : bone.children()) {
            cubes += countCubes(child);
        }
        return cubes;
    }

    private static String packFile(String pack, String path) {
        return "/assets/dawnduskterminal/ysm/" + pack + "/" + path;
    }
}
