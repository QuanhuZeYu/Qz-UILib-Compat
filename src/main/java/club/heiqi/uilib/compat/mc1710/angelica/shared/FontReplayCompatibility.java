package club.heiqi.uilib.compat.mc1710.angelica.shared;

import club.heiqi.uilib.host.client.font.context.FontRenderContext;

/** 按已捕获的完整来源值比较连续字体票据，保留原坐标并直接计算合并资格。
 * 相等时可在同一短段合并计划；回放仍按各自合同校验 context、pack 与资源代际。 */
final class FontReplayCompatibility {
    private FontReplayCompatibility() {}

    static boolean same(FontRenderContext a, FontRenderContext b,
            HostProgramSnapshot sa, HostProgramSnapshot sb) {
        // legacy 上下文缺少完整光栅事实时返回 false，合并资格要求来源值完整。
        if (a == null || b == null || a.rasterState == null || b.rasterState == null) {
            return false;
        }
        return sameSnapshot(sa, sb) && a.sameDrawState(b);
    }

    private static boolean sameSnapshot(HostProgramSnapshot a, HostProgramSnapshot b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.pipeline == b.pipeline
                && a.generation == b.generation
                && a.active == b.active
                && a.phase == b.phase
                && a.entityId == b.entityId
                && a.itemId == b.itemId
                && sameFloats(a.lightmapMatrix, b.lightmapMatrix)
                && (a.extended == b.extended
                        || a.extended != null && a.extended.sameValues(b.extended));
    }

    private static boolean sameFloats(float[] a, float[] b) {
        if (a == b) return true;
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) if (!sameFloat(a[i], b[i])) return false;
        return true;
    }

    private static boolean sameFloat(float a, float b) {
        return Float.floatToRawIntBits(a) == Float.floatToRawIntBits(b);
    }
}
