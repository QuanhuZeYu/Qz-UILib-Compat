package club.heiqi.uilib.compat.mc1710.angelica.shared;

/** 亮度来源随Angelica ABI变迁；不从原始driver猜测缓存状态。 */
public interface LightmapState {
    float x() throws ReflectiveOperationException;
    float y() throws ReflectiveOperationException;
    int packed() throws ReflectiveOperationException;
    static LightmapState bind(ClassLoader loader) throws ReflectiveOperationException {
        AngelicaVersion version = AngelicaVersions.select(loader);
        if (version == null) throw new IllegalStateException("Angelica亮度来源缺席");
        return version.lightmapState(loader);
    }
}
