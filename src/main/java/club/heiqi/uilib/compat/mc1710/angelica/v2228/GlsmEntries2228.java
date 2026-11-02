package club.heiqi.uilib.compat.mc1710.angelica.v2228;

import club.heiqi.uilib.compat.mc1710.angelica.shared.GlsmEntries;

/** 2.2.28仍未拆分GL入口：GLSM*分组类不存在，全部入口在GLStateManager。 */
public final class GlsmEntries2228 extends GlsmEntries {
    public static final Lookup LOOKUP =
            (loader, kind) ->
                    kind == Kind.GLSM
                            ? Class.forName(
                                    "com.gtnewhorizons.angelica.glsm.GLStateManager", false, loader)
                            : null;
    public GlsmEntries2228(ClassLoader loader) { super(loader, LOOKUP); }
}
