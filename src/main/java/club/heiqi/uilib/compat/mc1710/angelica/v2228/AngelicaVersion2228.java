package club.heiqi.uilib.compat.mc1710.angelica.v2228;

import club.heiqi.uilib.compat.mc1710.AngelicaAbi;
import club.heiqi.uilib.compat.mc1710.angelica.shared.AngelicaVersion;
import club.heiqi.uilib.compat.mc1710.angelica.shared.DrawState;
import club.heiqi.uilib.compat.mc1710.angelica.shared.GlsmEntries;
import club.heiqi.uilib.compat.mc1710.angelica.shared.LightmapState;

/** 2.2.28：pipeline helper返回void，draw/亮度随GLContextState，preDraw仍在GLStateManager。 */
public final class AngelicaVersion2228 implements AngelicaVersion {
    public String id() { return AngelicaAbi.V2228; }
    public GlsmEntries glsmEntries(ClassLoader loader) { return new GlsmEntries2228(loader); }
    public DrawState drawState(ClassLoader loader) throws ReflectiveOperationException { return new DrawState2228(loader); }
    public LightmapState lightmapState(ClassLoader loader) throws ReflectiveOperationException { return new LightmapState2228(loader); }
}
