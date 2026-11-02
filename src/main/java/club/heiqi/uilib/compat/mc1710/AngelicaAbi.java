package club.heiqi.uilib.compat.mc1710;

import club.heiqi.uilib.compat.mc1710.lwjgl3ify.sdl.SceneNativeInputSupport;

/** 读资源而不提前加载Mixin目标；descriptor决定ABI，版本字符串不代替结构证据。
 * v2210=2.2.10（pipeline helper返回boolean，GL入口全在GLStateManager）；
 * v2228=2.2.28（helper返回void，GLStateManager仍持有全部GL入口）；
 * v2243=2.2.40+（helper返回void，GL入口已拆到GLSM*分组类）。 */
public final class AngelicaAbi {
    public static final String V2210 = "v2210", V2228 = "v2228", V2243 = "v2243";
    private AngelicaAbi() {}
    public static String select(ClassLoader loader) {
        String owner = "com/gtnewhorizons/angelica/client/font/BatchingFontRenderer";
        if (loader.getResource(owner + ".class") == null) return null;
        String args = "(L" + owner + ";[L" + owner + "$FontDrawCmd;IIIFFF)";
        try {
            if (SceneNativeInputSupport.member(loader, owner + ".class", true, "emitRangeThroughPipeline", args + "Z", 0x0008)) return V2210;
            if (SceneNativeInputSupport.member(loader, owner + ".class", true, "emitRangeThroughPipeline", args + "V", 0x0008))
                return loader.getResource("com/gtnewhorizons/angelica/glsm/GLSMQueries.class") == null ? V2228 : V2243;
        } catch (java.io.IOException failure) { throw new IllegalStateException("无法读取Angelica ABI", failure); }
        throw new IllegalStateException("Angelica存在但pipeline helper ABI不受支持");
    }
    /** 2.2.28与2.2.40+的字体管线helper与接缝相同，共用v2228的Mixin实现。 */
    public static String mixinFamily(String abi) { return V2243.equals(abi) ? V2228 : abi; }
}
