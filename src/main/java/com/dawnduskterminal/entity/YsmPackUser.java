package com.dawnduskterminal.entity;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 接受 YSM 模型包驱动的实体
 *
 * <p>三组能力都靠「值 + 递增序号」同步到客户端：客户端只在序号变化时应用一次，
 * 因此不需要逐帧同步，也不会因为丢包而停在错误状态。
 *
 * <ul>
 *   <li><b>模型包</b>：{@code setYsmPack} 换整套模型，空串表示用实体的默认包
 *   <li><b>外观开关</b>：{@code setRoaming} 写 Molang 的 {@code v.roaming.*} 与 {@code v.cloth} 等，
 *       模型包的 {@code pre_parallel2} 据此决定部件是否显示
 *   <li><b>手动动作</b>：{@code playAction} 播模型包 {@code extra_animation} 里声明的动作
 * </ul>
 *
 * <p>实现方负责把这几项放进 {@code SynchedEntityData}；{@link #mergeRoaming} 与
 * {@link #parseRoaming} 提供 {@code hat=1.0;cloth=2.0} 这种格式的编解码，免得两个实体各写一遍。
 */
public interface YsmPackUser {
    void setYsmPack(String id);

    String ysmPackId();

    int ysmPackSeq();

    void setRoaming(String key, double value);

    String roamingBlob();

    int roamingSeq();

    void playAction(String key);

    String actionKey();

    int actionSeq();

    /** 往 {@code hat=1.0;cloth=2.0} 里写一项，同名键覆盖；坏掉的旧项直接丢弃 */
    static String mergeRoaming(String blob, String key, double value) {
        Map<String, Double> values = parseRoaming(blob);
        values.put(key, value);
        StringBuilder merged = new StringBuilder();
        values.forEach((name, number) -> {
            if (!merged.isEmpty()) {
                merged.append(';');
            }
            merged.append(name).append('=').append(number);
        });
        return merged.toString();
    }

    static Map<String, Double> parseRoaming(String blob) {
        Map<String, Double> values = new LinkedHashMap<>();
        for (String entry : blob.split(";")) {
            int split = entry.indexOf('=');
            if (split <= 0) {
                continue;
            }
            try {
                values.put(entry.substring(0, split), Double.parseDouble(entry.substring(split + 1)));
            } catch (NumberFormatException ignored) {
                // 坏掉的一项跳过即可，不值得为它中断整条命令
            }
        }
        return values;
    }
}
