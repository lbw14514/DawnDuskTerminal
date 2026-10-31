package com.dawnduskterminal.client;

import com.dawnduskterminal.DawnDuskTerminal;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「转换时被归零的动态通道」清单，以及首次渲染时的自检
 *
 * <p>清单由 {@code tools/ysm/runtime_contract.py} 从源 YSM 动画与转换后动画的差异生成，放在
 * {@code assets/dawnduskterminal/ysm_runtime/<model>.json}。每一条记录的都是同一件事：
 * 源表达式里有 {@code query} / 变量 / 函数调用，转换器没有等价物于是写成 {@code 0}，
 * 必须由运行期逐帧补回。
 *
 * <p>逐帧写骨骼的那一层（{@link GhastEliteBonePose} 一类）在写入时调用 {@link #declare}，
 * 首次渲染结束后调用 {@link #auditOnce}：某条通道没人写，就说明「物理层没接上」，此时打一条警告。
 * 此前这类问题是静默的——文件能加载、片段能播放，只是那些通道恒为 0，表现为「动画有点僵」。
 *
 * <p>清单缺失时不阻塞渲染，只警告一次。
 */
public final class YsmRuntimeContract {
    private static final String FOLDER = "/assets/dawnduskterminal/ysm_runtime/";
    /** 解析结果按模型缓存，每次 {@link #load} 返回独立实例，写入记录互不干扰 */
    private static final Map<String, Parsed> PARSED = new ConcurrentHashMap<>();

    /** 一条待补回的通道；{@code axis} 为 0 / 1 / 2，对应 X / Y / Z */
    public record Channel(String clip, String bone, int axis, String expression, String converted) {}

    private record Parsed(List<Channel> pose, Map<String, String> skipped) {}

    private final String model;
    private final List<Channel> pose;
    private final Map<String, String> skipped;
    private final List<Channel> missing;
    private boolean audited;

    private YsmRuntimeContract(String model, List<Channel> pose, Map<String, String> skipped) {
        this.model = model;
        this.pose = List.copyOf(pose);
        this.skipped = Map.copyOf(skipped);
        this.missing = new ArrayList<>(pose);
    }

    public static YsmRuntimeContract load(String model) {
        Parsed parsed = PARSED.computeIfAbsent(model, YsmRuntimeContract::parse);
        return new YsmRuntimeContract(model, parsed.pose(), parsed.skipped());
    }

    /** 本模型必须由运行期补回的全部通道 */
    public List<Channel> poseChannels() {
        return this.pose;
    }

    /** 源文件里有、但整段没有播放的片段 */
    public Map<String, String> skippedClips() {
        return this.skipped;
    }

    /** 由逐帧写骨骼的那一层调用，声明「这个骨骼的这个轴我写了」 */
    public void declare(String bone, int axis) {
        this.missing.removeIf(channel -> channel.axis() == axis && channel.bone().equals(bone));
    }

    /** 尚未被任何写入覆盖的通道：非空即物理层没接上 */
    public List<Channel> missing() {
        return List.copyOf(this.missing);
    }

    /** 首次渲染结束时调用；缺失的通道逐条警告，只报一次 */
    public void auditOnce() {
        if (this.audited) {
            return;
        }
        this.audited = true;
        for (Channel channel : this.missing) {
            DawnDuskTerminal.LOGGER.warn(
                "{}：{}/{} 的第 {} 轴在源动画里是 {}，转换时被归零，运行期没有代码补回",
                this.model, channel.bone(), channel.clip(), channel.axis(), channel.expression());
        }
    }

    private static Parsed parse(String model) {
        String path = FOLDER + model + ".json";
        try (InputStream stream = YsmRuntimeContract.class.getResourceAsStream(path)) {
            if (stream == null) {
                DawnDuskTerminal.LOGGER.warn(
                    "缺少运行期损失清单 {}：用 tools/ysm/runtime_contract.py 生成", path);
                return new Parsed(List.of(), Map.of());
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return parse(JsonParser.parseReader(reader).getAsJsonObject());
            }
        } catch (IOException | RuntimeException error) {
            DawnDuskTerminal.LOGGER.warn("运行期损失清单 {} 读取失败", path, error);
            return new Parsed(List.of(), Map.of());
        }
    }

    private static Parsed parse(JsonObject root) {
        List<Channel> pose = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("channels")) {
            JsonObject channel = element.getAsJsonObject();
            pose.add(new Channel(
                channel.get("clip").getAsString(),
                channel.get("bone").getAsString(),
                channel.get("axis").getAsInt(),
                channel.get("expression").getAsString(),
                channel.get("converted").getAsString()));
        }
        Map<String, String> skipped = new LinkedHashMap<>();
        JsonObject skippedObject = root.getAsJsonObject("skipped_clips");
        if (skippedObject != null) {
            for (Map.Entry<String, JsonElement> entry : skippedObject.entrySet()) {
                skipped.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        return new Parsed(pose, skipped);
    }
}
