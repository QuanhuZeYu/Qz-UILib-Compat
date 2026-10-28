package club.heiqi.uilib.compat.mc1710.angelica.shared;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/** 按 Iris 输入状态的实际类型选择入口：2.2.10 使用构造器，2.2.28 枚举使用 of 工厂。 */
final class IrisInputAvailability {
    private final Constructor<?> constructor;
    private final Method factory;

    IrisInputAvailability(Class<?> type) throws ReflectiveOperationException {
        if (type.isEnum()) {
            factory = type.getMethod("of", boolean.class, boolean.class);
            constructor = null;
        } else {
            constructor = type.getConstructor(boolean.class, boolean.class);
            factory = null;
        }
    }

    Object create(boolean texture, boolean lightmap) throws ReflectiveOperationException {
        return factory != null
                ? factory.invoke(null, texture, lightmap)
                : constructor.newInstance(texture, lightmap);
    }
}
