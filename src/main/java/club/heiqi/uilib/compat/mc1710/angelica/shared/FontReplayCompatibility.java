package club.heiqi.uilib.compat.mc1710.angelica.shared;

import club.heiqi.uilib.host.client.font.context.FontProjection;
import club.heiqi.uilib.host.client.font.context.FontRenderContext;
import club.heiqi.uilib.host.client.font.context.FontRenderContext.MatrixState;
import club.heiqi.uilib.host.client.font.context.FontRenderContext.RasterState;
import club.heiqi.uilib.host.client.font.context.FontRenderContext.StencilFace;
import java.util.Arrays;

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
        boolean compatible = sameSnapshot(sa, sb)
                && a.contextIdentity == b.contextIdentity
                && a.revision == b.revision
                && a.passKey == b.passKey
                && a.drawFramebuffer == b.drawFramebuffer
                && a.sourceFramebufferHeight == b.sourceFramebufferHeight
                && a.depthTest == b.depthTest
                && a.depthFunction == b.depthFunction
                && a.depthWrite == b.depthWrite
                && a.scissorEnabled == b.scissorEnabled
                && (a == b || Arrays.equals(a.scissor(), b.scissor())
                        && sameProjection(a.projection, b.projection)
                        && sameMatrixState(a.state, b.state)
                        && sameRasterState(a.rasterState, b.rasterState));
        return compatible;
    }

    private static boolean sameProjection(FontProjection a, FontProjection b) {
        // P/MV各自相等才能保持shader语义，不能仅比较它们的乘积。
        return a == b || sameFloats(a.projection(), b.projection())
                && sameFloats(a.modelView(), b.modelView())
                && Arrays.equals(a.viewport(), b.viewport());
    }

    private static boolean sameMatrixState(MatrixState a, MatrixState b) {
        return a == b || a.alphaTest == b.alphaTest
                && a.alphaFunction == b.alphaFunction
                && sameFloat(a.alphaReference, b.alphaReference)
                && a.lightmapActive == b.lightmapActive
                && a.lightmapTexture == b.lightmapTexture
                && sameFloat(a.lightmapU, b.lightmapU)
                && sameFloat(a.lightmapV, b.lightmapV)
                && a.packedLight == b.packedLight
                && a.blockEntityId == b.blockEntityId
                && sameFloats(a.projection(), b.projection())
                && sameFloats(a.modelView(), b.modelView())
                && sameFloats(a.color(), b.color())
                && sameFloats(a.normal(), b.normal())
                && sameFloats(a.lighting(), b.lighting());
    }

    private static boolean sameRasterState(RasterState a, RasterState b) {
        return a == b || a.blendEnabled == b.blendEnabled
                && a.blendSrcRgb == b.blendSrcRgb
                && a.blendDstRgb == b.blendDstRgb
                && a.blendSrcAlpha == b.blendSrcAlpha
                && a.blendDstAlpha == b.blendDstAlpha
                && a.blendEquationRgb == b.blendEquationRgb
                && a.blendEquationAlpha == b.blendEquationAlpha
                && a.stencilEnabled == b.stencilEnabled
                && a.cullEnabled == b.cullEnabled
                && a.cullFace == b.cullFace
                && a.frontFace == b.frontFace
                && a.polygonOffsetFill == b.polygonOffsetFill
                && sameFloat(a.polygonOffsetFactor, b.polygonOffsetFactor)
                && sameFloat(a.polygonOffsetUnits, b.polygonOffsetUnits)
                && sameStencil(a.frontStencil, b.frontStencil)
                && sameStencil(a.backStencil, b.backStencil)
                && sameFloats(a.blendColor(), b.blendColor())
                && sameFloats(a.depthRange(), b.depthRange())
                && Arrays.equals(a.colorMask(), b.colorMask());
    }

    private static boolean sameStencil(StencilFace a, StencilFace b) {
        return a == b || a.function == b.function
                && a.reference == b.reference
                && a.valueMask == b.valueMask
                && a.writeMask == b.writeMask
                && a.fail == b.fail
                && a.depthFail == b.depthFail
                && a.depthPass == b.depthPass;
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
