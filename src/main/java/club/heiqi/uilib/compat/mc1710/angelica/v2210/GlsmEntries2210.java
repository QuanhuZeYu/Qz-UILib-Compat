package club.heiqi.uilib.compat.mc1710.angelica.v2210;

import club.heiqi.uilib.compat.mc1710.angelica.shared.GlsmEntries;

/** 2.2.10没有GLSM*分组类，provider/producer/snapshot所需入口全部仍在GLStateManager。 */
public final class GlsmEntries2210 extends GlsmEntries {
    public static final Lookup LOOKUP =
            (loader, kind) ->
                    kind == Kind.GLSM
                            ? Class.forName(
                                    "com.gtnewhorizons.angelica.glsm.GLStateManager", false, loader)
                            : null;
    public GlsmEntries2210(ClassLoader loader) { super(loader, LOOKUP); }
}
