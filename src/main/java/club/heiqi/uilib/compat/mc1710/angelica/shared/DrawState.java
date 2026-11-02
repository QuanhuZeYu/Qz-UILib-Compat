package club.heiqi.uilib.compat.mc1710.angelica.shared;

/** 每次操作读取所属上下文，不在scope创建时冻结另一次draw的GLContextState。 */
public interface DrawState {
    void beforeDraw(int mode) throws ReflectiveOperationException;
    void stabilizeHost() throws ReflectiveOperationException;
    void suspendHost() throws ReflectiveOperationException;
    void resumeHost() throws ReflectiveOperationException;
    void refreshUniforms(int program) throws ReflectiveOperationException;
    int vertexFlags() throws ReflectiveOperationException;
    static DrawState bind(ClassLoader loader) throws ReflectiveOperationException {
        AngelicaVersion version = AngelicaVersions.select(loader);
        if (version == null) throw new IllegalStateException("Angelica draw状态缺席");
        return version.drawState(loader);
    }
}
