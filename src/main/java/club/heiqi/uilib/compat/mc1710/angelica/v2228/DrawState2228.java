package club.heiqi.uilib.compat.mc1710.angelica.v2228;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import club.heiqi.uilib.compat.mc1710.angelica.shared.DrawState;

/** 2.2.28将draw和VAO事实归入当前上下文；每次使用ctx，不能缓存其对象。 */
public final class DrawState2228 implements DrawState {
    private final Method context, draw, refresh, flags;
    private final Field vaos;
    public DrawState2228(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> glsm = Class.forName("com.gtnewhorizons.angelica.glsm.GLStateManager", false, loader);
        context = glsm.getMethod("ctx"); Class<?> state = context.getReturnType();
        draw = glsm.getMethod("preDraw", state, int.class);
        refresh = Class.forName("com.gtnewhorizons.angelica.glsm.CompatUniformManager", false, loader).getMethod("refreshCompatUniforms", int.class, state);
        vaos = state.getField("vaos"); flags = vaos.getType().getMethod("getVertexFlags");
    }
    public void beforeDraw(int mode) throws ReflectiveOperationException { draw.invoke(null, context.invoke(null), mode); }
    public void refreshUniforms(int program) throws ReflectiveOperationException { refresh.invoke(null, program, context.invoke(null)); }
    public int vertexFlags() throws ReflectiveOperationException { return ((Number) flags.invoke(vaos.get(context.invoke(null)))).intValue(); }
}
