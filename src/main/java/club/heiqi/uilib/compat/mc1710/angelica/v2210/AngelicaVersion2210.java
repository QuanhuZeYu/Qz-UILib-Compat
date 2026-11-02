package club.heiqi.uilib.compat.mc1710.angelica.v2210;

import club.heiqi.uilib.compat.mc1710.AngelicaAbi;
import club.heiqi.uilib.compat.mc1710.angelica.shared.AngelicaVersion;
import club.heiqi.uilib.compat.mc1710.angelica.shared.DrawState;
import club.heiqi.uilib.compat.mc1710.angelica.shared.GlsmEntries;
import club.heiqi.uilib.compat.mc1710.angelica.shared.LightmapState;

/** 2.2.10：pipeline helper返回boolean，draw/亮度/GL入口均走静态GLStateManager。 */
public final class AngelicaVersion2210 implements AngelicaVersion {
    public String id() { return AngelicaAbi.V2210; }
    public GlsmEntries glsmEntries(ClassLoader loader) { return new GlsmEntries2210(loader); }
    public DrawState drawState(ClassLoader loader) throws ReflectiveOperationException { return new DrawState2210(loader); }
    public LightmapState lightmapState(ClassLoader loader) throws ReflectiveOperationException { return new LightmapState2210(loader); }
}
