package com.dawnduskterminal.client.animation;

import com.dawnduskterminal.geckolib3.core.molang.binding.ContextBinding;
import com.dawnduskterminal.geckolib3.core.molang.util.StringPool;
import com.dawnduskterminal.molang.runtime.Function;
import com.dawnduskterminal.molang.runtime.HashMapStruct;
import com.dawnduskterminal.molang.runtime.Struct;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * {@code fn.*}：模型包自带的用户函数
 *
 * <p>YSM 允许模型包在 {@code functions/*.molang} 里定义自己的函数，并在动画中以 {@code fn.名字}
 * 调用。本模组手上的两套模型包都没有带 {@code functions/} 目录，因此这五个四元数函数按标准语义重建：
 * 四元数为 {@code (w, x, y, z)}，{@code ol} 为欧拉角（度），
 * {@code QuaternionAndVector(向量, 四元数)} 的参数顺序由调用处确定。
 *
 * <p>名字一律按小写登记：词法层会把标识符转成小写，所以模型里写的 {@code fn.QuaternionToOl}
 * 查表时用的是 {@code quaterniontool}。
 *
 * <p>欧拉角一律按 {@code ZYX} 序：骨骼旋转按 {@code Rz·Ry·Rx} 合成，取 {@code XYZ} 的逆序会让
 * 40 度以上的角度在各轴之间错位（实测两条前腿不互为镜像、左右 x 与 z 互换）。
 */
public class FnBinding extends ContextBinding {
    public static final FnBinding INSTANCE = new FnBinding();

    private FnBinding() {
        function("oltoquaternion", (Function) (context, arguments) -> toStruct(new Quaternionf()
                .rotationZYX(
                        (float) Math.toRadians(arguments.getAsFloat(context, 0)),
                        (float) Math.toRadians(arguments.getAsFloat(context, 1)),
                        (float) Math.toRadians(arguments.getAsFloat(context, 2)))));
        function("quaternionmultiply", (Function)
                (context, arguments) -> toStruct(new Quaternionf(structToQuaternion(arguments.getValue(context, 0)))
                        .mul(structToQuaternion(arguments.getValue(context, 1)))));
        function("quaternionandvector", (Function)
                (context, arguments) -> toStruct(structToQuaternion(arguments.getValue(context, 1))
                        .transform(structToVector(arguments.getValue(context, 0)))));
        function("vectortovectorquaternion", (Function) (context, arguments) -> toStruct(new Quaternionf()
                .rotationTo(
                        structToVector(arguments.getValue(context, 0)).normalize(),
                        structToVector(arguments.getValue(context, 1)).normalize())));
        function("quaterniontool", (Function) (context, arguments) -> {
            Vector3f euler = structToQuaternion(arguments.getValue(context, 0)).getEulerAnglesZYX(new Vector3f());
            HashMapStruct struct = new HashMapStruct();
            struct.putProperty(StringPool.computeIfAbsent("x"), Math.toDegrees(euler.x));
            struct.putProperty(StringPool.computeIfAbsent("y"), Math.toDegrees(euler.y));
            struct.putProperty(StringPool.computeIfAbsent("z"), Math.toDegrees(euler.z));
            return struct;
        });
    }

    private static HashMapStruct toStruct(Quaternionf quaternion) {
        HashMapStruct struct = new HashMapStruct();
        struct.putProperty(StringPool.computeIfAbsent("w"), (double) quaternion.w);
        struct.putProperty(StringPool.computeIfAbsent("x"), (double) quaternion.x);
        struct.putProperty(StringPool.computeIfAbsent("y"), (double) quaternion.y);
        struct.putProperty(StringPool.computeIfAbsent("z"), (double) quaternion.z);
        return struct;
    }

    private static HashMapStruct toStruct(Vector3f vector) {
        HashMapStruct struct = new HashMapStruct();
        struct.putProperty(StringPool.computeIfAbsent("x"), (double) vector.x);
        struct.putProperty(StringPool.computeIfAbsent("y"), (double) vector.y);
        struct.putProperty(StringPool.computeIfAbsent("z"), (double) vector.z);
        return struct;
    }

    private static Quaternionf structToQuaternion(Object value) {
        if (!(value instanceof Struct struct)) {
            return new Quaternionf();
        }
        return new Quaternionf(number(struct, "x"), number(struct, "y"), number(struct, "z"), number(struct, "w"));
    }

    private static Vector3f structToVector(Object value) {
        if (!(value instanceof Struct struct)) {
            return new Vector3f();
        }
        return new Vector3f((float) number(struct, "x"), (float) number(struct, "y"), (float) number(struct, "z"));
    }

    private static double number(Struct struct, String name) {
        Object value = struct.getProperty(StringPool.computeIfAbsent(name));
        return value instanceof Number number ? number.doubleValue() : 0.0D;
    }
}
