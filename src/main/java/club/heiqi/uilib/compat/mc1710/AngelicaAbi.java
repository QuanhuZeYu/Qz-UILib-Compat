package club.heiqi.uilib.compat.mc1710;

import club.heiqi.uilib.compat.mc1710.lwjgl3ify.sdl.SceneNativeInputSupport;

/** 从 class 资源的 helper descriptor 选择 ABI，目标类由 Mixin 阶段加载；结构失配时报告错误。 */
public final class AngelicaAbi {
    private AngelicaAbi() {}
    public static String select(ClassLoader loader) {
        String owner = "com/gtnewhorizons/angelica/client/font/BatchingFontRenderer";
        if (loader.getResource(owner + ".class") == null) return null;
        String args = "(L" + owner + ";[L" + owner + "$FontDrawCmd;IIIFFF)";
        try {
            if (SceneNativeInputSupport.member(loader, owner + ".class", true, "emitRangeThroughPipeline", args + "Z", 0x0008)) return "v2210";
            if (SceneNativeInputSupport.member(loader, owner + ".class", true, "emitRangeThroughPipeline", args + "V", 0x0008)) return "v2228";
        } catch (java.io.IOException failure) { throw new IllegalStateException("无法读取Angelica ABI", failure); }
        throw new IllegalStateException("Angelica存在但pipeline helper ABI不受支持");
    }
}
