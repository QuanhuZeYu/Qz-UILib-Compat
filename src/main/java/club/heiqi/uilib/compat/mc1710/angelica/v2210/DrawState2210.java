package club.heiqi.uilib.compat.mc1710.angelica.v2210;

import java.lang.reflect.Method;
import club.heiqi.uilib.compat.mc1710.angelica.shared.DrawState;

/** 2.2.10静态draw及VAO入口。 */
public final class DrawState2210 implements DrawState {
    private final Method draw, refresh, flags;
    public DrawState2210(ClassLoader loader) throws ReflectiveOperationException {
        draw = Class.forName("com.gtnewhorizons.angelica.glsm.GLStateManager", false, loader).getMethod("preDraw", int.class);
        refresh = Class.forName("com.gtnewhorizons.angelica.glsm.CompatUniformManager", false, loader).getMethod("refreshCompatUniforms", int.class);
        flags = Class.forName("com.gtnewhorizons.angelica.glsm.ffp.VAOManager", false, loader).getMethod("getCurrentVertexFlags");
    }
    // 2.2.10没有状态变更自动flush协议；保持既有提交屏障，不强制结束TESR pass。
    public void stabilizeHost() {}
    public void suspendHost() {}
    public void resumeHost() {}
    public void beforeDraw(int mode) throws ReflectiveOperationException { draw.invoke(null, mode); }
    public void refreshUniforms(int program) throws ReflectiveOperationException { refresh.invoke(null, program); }
    public int vertexFlags() throws ReflectiveOperationException { return ((Number) flags.invoke(null)).intValue(); }
}
