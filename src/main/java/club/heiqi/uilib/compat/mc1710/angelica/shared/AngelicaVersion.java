package club.heiqi.uilib.compat.mc1710.angelica.shared;

/** Angelica版本适配入口；具体绑定在各版本目录，本接口只供共享逻辑按ABI取用。 */
public interface AngelicaVersion {
    String id();
    GlsmEntries glsmEntries(ClassLoader loader);
    DrawState drawState(ClassLoader loader) throws ReflectiveOperationException;
    LightmapState lightmapState(ClassLoader loader) throws ReflectiveOperationException;
}
