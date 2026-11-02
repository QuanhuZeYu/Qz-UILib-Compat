package club.heiqi.uilib.compat.mc1710.angelica.shared;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** 2.2.28起draw与VAO事实归入当前GLContextState；preDraw的归属类由版本Lookup决定，
 * 2.2.28仍在GLStateManager，2.2.40+迁到GLSMDraws。每次使用ctx，不能缓存其对象。 */
public abstract class ContextDrawState implements DrawState {
    private final Method context, draw, refresh, flags, stabilize, suspend, resume;
    private final Field vaos;

    protected ContextDrawState(ClassLoader loader, GlsmEntries.Lookup lookup) throws ReflectiveOperationException {
        Class<?> glsm = lookup.type(loader, GlsmEntries.Kind.GLSM);
        if (glsm == null) throw new IllegalStateException("Angelica GLStateManager缺席");
        stabilize = glsm.getMethod("beforeUncapturedStateChange");
        Class<?> guard = Class.forName("com.gtnewhorizons.angelica.glsm.hooks.BatchStateGuard", false, loader);
        suspend = guard.getMethod("suspend");
        resume = guard.getMethod("resume");
        context = glsm.getMethod("ctx");
        Class<?> state = context.getReturnType();
        draw = GlsmEntries.method(lookup.type(loader, GlsmEntries.Kind.DRAWING), glsm, "preDraw", state, int.class);
        refresh =
                Class.forName("com.gtnewhorizons.angelica.glsm.CompatUniformManager", false, loader)
                        .getMethod("refreshCompatUniforms", int.class, state);
        vaos = state.getField("vaos");
        flags = vaos.getType().getMethod("getVertexFlags");
    }

    public void stabilizeHost() throws ReflectiveOperationException { stabilize.invoke(null); }
    public void suspendHost() throws ReflectiveOperationException { suspend.invoke(null); }
    public void resumeHost() throws ReflectiveOperationException { resume.invoke(null); }
    public void beforeDraw(int mode) throws ReflectiveOperationException { draw.invoke(null, context.invoke(null), mode); }
    public void refreshUniforms(int program) throws ReflectiveOperationException { refresh.invoke(null, program, context.invoke(null)); }
    public int vertexFlags() throws ReflectiveOperationException { return ((Number) flags.invoke(vaos.get(context.invoke(null)))).intValue(); }
}
