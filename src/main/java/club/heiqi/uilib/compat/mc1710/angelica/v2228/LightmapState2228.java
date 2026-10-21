package club.heiqi.uilib.compat.mc1710.angelica.v2228;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import club.heiqi.uilib.compat.mc1710.angelica.shared.LightmapState;

/** 2.2.28将亮度迁入当前GLContextState，packed值也读取同一上下文。 */
public final class LightmapState2228 implements LightmapState {
    private final Field x, y;
    private final Method context, packed;
    public LightmapState2228(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> glsm = Class.forName("com.gtnewhorizons.angelica.glsm.GLStateManager", false, loader);
        context = glsm.getMethod("ctx");
        x = context.getReturnType().getField("lastBrightnessX"); y = context.getReturnType().getField("lastBrightnessY");
        Class<?> config = Class.forName("com.gtnewhorizons.angelica.glsm.hooks.GLSMConfig", false, loader);
        packed = config.getMethod("packedLastBrightness");
    }
    public float x() throws ReflectiveOperationException { return x.getFloat(context.invoke(null)); }
    public float y() throws ReflectiveOperationException { return y.getFloat(context.invoke(null)); }
    public int packed() throws ReflectiveOperationException { return ((Number) packed.invoke(null)).intValue(); }
}
