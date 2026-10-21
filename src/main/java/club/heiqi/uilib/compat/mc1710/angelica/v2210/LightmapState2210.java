package club.heiqi.uilib.compat.mc1710.angelica.v2210;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import club.heiqi.uilib.compat.mc1710.angelica.shared.LightmapState;

/** 2.2.10的亮度在GLSMConfig静态字段，GLContextState尚未持有它们。 */
public final class LightmapState2210 implements LightmapState {
    private final Field x, y;
    private final Method packed;
    public LightmapState2210(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> config = Class.forName("com.gtnewhorizons.angelica.glsm.hooks.GLSMConfig", false, loader);
        x = config.getField("lastBrightnessX"); y = config.getField("lastBrightnessY");
        packed = config.getMethod("packedLastBrightness");
    }
    public float x() throws ReflectiveOperationException { return x.getFloat(null); }
    public float y() throws ReflectiveOperationException { return y.getFloat(null); }
    public int packed() throws ReflectiveOperationException { return ((Number) packed.invoke(null)).intValue(); }
}
