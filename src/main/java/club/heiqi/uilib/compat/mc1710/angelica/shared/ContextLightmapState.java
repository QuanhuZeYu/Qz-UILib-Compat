package club.heiqi.uilib.compat.mc1710.angelica.shared;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** 2.2.28起亮度迁入当前GLContextState，packed值读同一宿主的GLSMConfig。 */
public abstract class ContextLightmapState implements LightmapState {
    private final Field x, y;
    private final Method context, packed;

    protected ContextLightmapState(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> glsm = Class.forName("com.gtnewhorizons.angelica.glsm.GLStateManager", false, loader);
        context = glsm.getMethod("ctx");
        Class<?> state = context.getReturnType();
        x = state.getField("lastBrightnessX");
        y = state.getField("lastBrightnessY");
        Class<?> config = Class.forName("com.gtnewhorizons.angelica.glsm.hooks.GLSMConfig", false, loader);
        packed = config.getMethod("packedLastBrightness");
    }

    public float x() throws ReflectiveOperationException { return x.getFloat(context.invoke(null)); }
    public float y() throws ReflectiveOperationException { return y.getFloat(context.invoke(null)); }
    public int packed() throws ReflectiveOperationException { return ((Number) packed.invoke(null)).intValue(); }
}
