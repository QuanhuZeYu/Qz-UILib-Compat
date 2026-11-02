package club.heiqi.uilib.compat.mc1710.angelica.v2243;

import club.heiqi.uilib.compat.mc1710.AngelicaAbi;
import club.heiqi.uilib.compat.mc1710.angelica.shared.AngelicaVersion;
import club.heiqi.uilib.compat.mc1710.angelica.shared.DrawState;
import club.heiqi.uilib.compat.mc1710.angelica.shared.GlsmEntries;
import club.heiqi.uilib.compat.mc1710.angelica.shared.LightmapState;

/** 2.2.40+：pipeline helper仍返回void，但GL入口已拆到GLSM*，preDraw在GLSMDraws。 */
public final class AngelicaVersion2243 implements AngelicaVersion {
    public String id() { return AngelicaAbi.V2243; }
    public GlsmEntries glsmEntries(ClassLoader loader) { return new GlsmEntries2243(loader); }
    public DrawState drawState(ClassLoader loader) throws ReflectiveOperationException { return new DrawState2243(loader); }
    public LightmapState lightmapState(ClassLoader loader) throws ReflectiveOperationException { return new LightmapState2243(loader); }
}
