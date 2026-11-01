package club.heiqi.uilib.compat.mc1710.angelica.shared;

import java.lang.reflect.Method;

/** Angelica新版本把GL入口从GLStateManager拆到GLSM*，旧版本（2.2.10、2.2.28）仍挂在GLStateManager。
 * 拆分入口按class资源真实存在选择，每个入口保留旧位置回退；两处都缺席才是真实ABI不匹配。
 * 只读资源、不触发目标静态初始化；只提供原始header的ABI校验loader不会用空壳占位冒充目标。 */
public final class GlsmAbi {
    private GlsmAbi() {}

    public static Class<?> type(ClassLoader loader, String name) throws ClassNotFoundException {
        if (loader.getResource(name.replace('.', '/') + ".class") == null) return null;
        return Class.forName(name, false, loader);
    }

    public static Method method(Class<?> split, Class<?> glsm, String name, Class<?>... parameters)
            throws NoSuchMethodException {
        if (split != null) {
            try {
                return split.getMethod(name, parameters);
            } catch (NoSuchMethodException legacyShape) {
                // 迁移中的版本可能只挪走部分入口，继续按旧位置解析。
            }
        }
        return glsm.getMethod(name, parameters);
    }
}
