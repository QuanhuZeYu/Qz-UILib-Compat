package club.heiqi.uilib.compat.mc1710.angelica.shared;

import club.heiqi.uilib.compat.mc1710.AngelicaAbi;

/** 每次操作读取所属 draw 的当前上下文，使短 scope 跟随有效 GLContextState。 */
public interface DrawState {
    void beforeDraw(int mode) throws ReflectiveOperationException;
    void stabilizeHost() throws ReflectiveOperationException;
    void suspendHost() throws ReflectiveOperationException;
    void resumeHost() throws ReflectiveOperationException;
    void refreshUniforms(int program) throws ReflectiveOperationException;
    int vertexFlags() throws ReflectiveOperationException;
    static DrawState bind(ClassLoader loader) throws ReflectiveOperationException {
        if ("v2210".equals(AngelicaAbi.select(loader))) return new club.heiqi.uilib.compat.mc1710.angelica.v2210.DrawState2210(loader);
        return new club.heiqi.uilib.compat.mc1710.angelica.v2228.DrawState2228(loader);
    }
}
