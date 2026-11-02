package club.heiqi.uilib.compat.mc1710.angelica.v2228;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import club.heiqi.uilib.compat.mc1710.angelica.shared.DrawState;

/** 2.2.28 的 draw 与 VAO 状态由当前上下文持有，每次操作重新读取 ctx。 */
public final class DrawState2228 implements DrawState {
    private final Method context, draw, refresh, flags, stabilize, suspend, resume;
    private final Field vaos;
    public DrawState2228(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> glsm = Class.forName("com.gtnewhorizons.angelica.glsm.GLStateManager", false, loader);
        stabilize = glsm.getMethod("beforeUncapturedStateChange");
        Class<?> guard = Class.forName("com.gtnewhorizons.angelica.glsm.hooks.BatchStateGuard", false, loader);
        suspend = guard.getMethod("suspend"); resume = guard.getMethod("resume");
        context = glsm.getMethod("ctx"); Class<?> state = context.getReturnType();
        draw = club.heiqi.uilib.compat.mc1710.AngelicaAbi.states(glsm).method("preDraw", state, int.class);
        refresh = Class.forName("com.gtnewhorizons.angelica.glsm.CompatUniformManager", false, loader).getMethod("refreshCompatUniforms", int.class, state);
        if (refresh.getReturnType() != boolean.class)
            throw new NoSuchMethodException("Angelica refreshCompatUniforms必须返回boolean: " + refresh);
        vaos = state.getField("vaos"); flags = vaos.getType().getMethod("getVertexFlags");
    }
    public void stabilizeHost() throws ReflectiveOperationException { stabilize.invoke(null); }
    public void suspendHost() throws ReflectiveOperationException { suspend.invoke(null); }
    public void resumeHost() throws ReflectiveOperationException { resume.invoke(null); }
    public void beforeDraw(int mode) throws ReflectiveOperationException { draw.invoke(null, context.invoke(null), mode); }
    // boolean说明program是否登记compat项；这里仅触发按generation刷新，不把它当本次upload的结果。
    public void refreshUniforms(int program) throws ReflectiveOperationException { refresh.invoke(null, program, context.invoke(null)); }
    public int vertexFlags() throws ReflectiveOperationException { return ((Number) flags.invoke(vaos.get(context.invoke(null)))).intValue(); }
}
