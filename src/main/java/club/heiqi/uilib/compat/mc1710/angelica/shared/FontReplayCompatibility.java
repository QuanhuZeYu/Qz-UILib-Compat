package club.heiqi.uilib.compat.mc1710.angelica.shared;

import club.heiqi.uilib.host.client.font.context.FontRenderContext;

/** 连续字体票据的完整来源值比较；不查询GL、不重映射坐标，也不持有额外状态。
 * 相等只允许同一短段内合计划，不能替代回放时的context、pack和资源代际校验。 */
final class FontReplayCompatibility {
    private FontReplayCompatibility() {}

    static boolean same(FontRenderContext a, FontRenderContext b,
            HostProgramSnapshot sa, HostProgramSnapshot sb) {
        // legacy上下文缺少完整光栅事实，即使对象相同也不据此合并。
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
