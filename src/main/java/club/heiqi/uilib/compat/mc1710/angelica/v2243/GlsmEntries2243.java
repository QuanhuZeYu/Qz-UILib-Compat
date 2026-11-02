package club.heiqi.uilib.compat.mc1710.angelica.v2243;

import club.heiqi.uilib.compat.mc1710.angelica.shared.GlsmEntries;

/** 2.2.40+把GL入口拆到GLSM*分组类；只有isCachingEnabled等少量入口仍留在GLStateManager。 */
public final class GlsmEntries2243 extends GlsmEntries {
    private static final String ROOT = "com.gtnewhorizons.angelica.glsm.";
    public static final Lookup LOOKUP =
            (loader, kind) -> {
                switch (kind) {
                    case GLSM: return Class.forName(ROOT + "GLStateManager", false, loader);
                    case DRAWING: return Class.forName(ROOT + "GLSMDraws", false, loader);
                    case QUERIES: return Class.forName(ROOT + "GLSMQueries", false, loader);
                    case ENABLE: return Class.forName(ROOT + "GLSMEnable", false, loader);
                    case DEPTH_STENCIL_ALPHA:
                        return Class.forName(ROOT + "GLSMDepthStencilAlpha", false, loader);
                    case BLEND: return Class.forName(ROOT + "GLSMBlend", false, loader);
                    case RASTERIZER: return Class.forName(ROOT + "GLSMRasterizer", false, loader);
                    case TEXTURES: return Class.forName(ROOT + "GLSMTextures", false, loader);
                    case MATRICES: return Class.forName(ROOT + "GLSMMatrices", false, loader);
                    case VERTEX_INPUT: return Class.forName(ROOT + "GLSMVertexInput", false, loader);
                    case BUFFERS: return Class.forName(ROOT + "GLSMBuffers", false, loader);
                    case CURRENT_ATTRIBS:
                        return Class.forName(ROOT + "GLSMCurrentAttribs", false, loader);
                    default: return null;
                }
            };
    public GlsmEntries2243(ClassLoader loader) { super(loader, LOOKUP); }
}
