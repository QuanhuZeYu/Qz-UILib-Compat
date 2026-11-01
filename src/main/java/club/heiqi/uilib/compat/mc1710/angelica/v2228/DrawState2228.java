package club.heiqi.uilib.compat.mc1710.angelica.v2228;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import club.heiqi.uilib.compat.mc1710.angelica.shared.DrawState;
import club.heiqi.uilib.compat.mc1710.angelica.shared.GlsmAbi;

/** 2.2.28将draw和VAO事实归入当前上下文；后续版本又把preDraw移到GLSMDraws，仍按上下文读取。 */
public final class DrawState2228 implements DrawState {
    private final Method context, draw, refresh, flags, stabilize, suspend, resume;
    private final Field vaos;
    public DrawState2228(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> glsm = Class.forName("com.gtnewhorizons.angelica.glsm.GLStateManager", false, loader);
        stabilize = glsm.getMethod("beforeUncapturedStateChange");
        Class<?> guard = Class.forName("com.gtnewhorizons.angelica.glsm.hooks.BatchStateGuard", false, loader);
        suspend = guard.getMethod("suspend"); resume = guard.getMethod("resume");
        context = glsm.getMethod("ctx"); Class<?> state = context.getReturnType();
        draw = preDraw(loader, glsm, state);
        refresh = Class.forName("com.gtnewhorizons.angelica.glsm.CompatUniformManager", false, loader).getMethod("refreshCompatUniforms", int.class, state);
        vaos = state.getField("vaos"); flags = vaos.getType().getMethod("getVertexFlags");
    }
    /** 2.2.28在GLStateManager，2.2.43迁到GLSMDraws；只认实际存在的类，不用空壳占位。 */
    private static Method preDraw(ClassLoader loader, Class<?> glsm, Class<?> state) throws ReflectiveOperationException {
        Class<?> draws = GlsmAbi.type(loader, "com.gtnewhorizons.angelica.glsm.GLSMDraws");
        return GlsmAbi.method(draws, glsm, "preDraw", state, int.class);
    }
    public void stabilizeHost() throws ReflectiveOperationException { stabilize.invoke(null); }
    public void suspendHost() throws ReflectiveOperationException { suspend.invoke(null); }
    public void resumeHost() throws ReflectiveOperationException { resume.invoke(null); }
    public void beforeDraw(int mode) throws ReflectiveOperationException { draw.invoke(null, context.invoke(null), mode); }
    public void refreshUniforms(int program) throws ReflectiveOperationException { refresh.invoke(null, program, context.invoke(null)); }
    public int vertexFlags() throws ReflectiveOperationException { return ((Number) flags.invoke(vaos.get(context.invoke(null)))).intValue(); }
}
