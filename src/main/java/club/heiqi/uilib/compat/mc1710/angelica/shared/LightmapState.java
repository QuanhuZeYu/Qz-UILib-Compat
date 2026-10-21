package club.heiqi.uilib.compat.mc1710.angelica.shared;

import club.heiqi.uilib.compat.mc1710.AngelicaAbi;

/** 亮度来源随Angelica ABI变迁；不从原始driver猜测缓存状态。 */
public interface LightmapState {
    float x() throws ReflectiveOperationException;
    float y() throws ReflectiveOperationException;
    int packed() throws ReflectiveOperationException;
    static LightmapState bind(ClassLoader loader) throws ReflectiveOperationException {
        String abi = AngelicaAbi.select(loader);
        if ("v2210".equals(abi)) return new club.heiqi.uilib.compat.mc1710.angelica.v2210.LightmapState2210(loader);
        if ("v2228".equals(abi)) return new club.heiqi.uilib.compat.mc1710.angelica.v2228.LightmapState2228(loader);
        throw new IllegalStateException("Angelica亮度来源缺席");
    }
}
