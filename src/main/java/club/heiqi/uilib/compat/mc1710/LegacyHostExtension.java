package club.heiqi.uilib.compat.mc1710;

import club.heiqi.uilib.host.client.extension.MinecraftHostExtension;
import club.heiqi.uilib.host.client.gl.*;
import club.heiqi.uilib.host.client.font.compat.OptionalFontHostBridge;
import club.heiqi.uilib.host.client.font.context.LwjglFontContextCapture;
import club.heiqi.uilib.host.client.image.ItemStateScope;
import club.heiqi.uilib.ui.host.minecraft.SceneNativeTextInput;
import club.heiqi.uilib.compat.mc1710.angelica.shared.*;
import club.heiqi.uilib.compat.mc1710.lwjgl3ify.sdl.Lwjgl3ifyTextInput;

/** 服务构造不解析目标Mod；只有目标资源和所需ABI已确认才创建专属适配器。 */
public final class LegacyHostExtension implements MinecraftHostExtension {
    private static boolean present(ClassLoader loader, String type) { return loader.getResource(type.replace('.', '/') + ".class") != null; }
    public OptionalFontHostBridge fontBridge(ClassLoader loader, GlApi gl,
            OptionalFontHostBridge.ReplaySink sink, int capacity) {
        if (!present(loader, "com.gtnewhorizons.angelica.client.font.BatchingFontRenderer")) return null;
        AngelicaAbi.select(loader);
        try { return new AngelicaBridge(Class.forName("com.gtnewhorizons.angelica.client.font.BatchingFontRenderer", false, loader), loader, gl, sink, capacity); }
        catch (ClassNotFoundException error) { throw new IllegalStateException(error); }
    }
    public LwjglFontContextCapture.MatrixStateProvider matrices(ClassLoader loader, GlApi gl) {
        if (!present(loader, "com.gtnewhorizons.angelica.glsm.GLStateManager")) return null;
        return AngelicaMatrixStateProvider.optional(loader, gl);
    }
    public GlProgramProvider programs(ClassLoader loader, GlApi gl) {
        return present(loader, "net.coderbot.iris.Iris") ? new IrisProgramProvider(loader, gl) : null;
    }
    public ItemStateScope.Port itemPort(ClassLoader loader) {
        return present(loader, "com.gtnewhorizons.angelica.glsm.GLStateManager") ? new AngelicaItemPort(loader) : null;
    }
    public Runnable captureItemContext(ClassLoader loader) {
        AngelicaItemPort.Captured captured = AngelicaItemPort.Captured.read(loader);
        return captured == null ? null : captured::restore;
    }
    public boolean nativeInputSupported(ClassLoader loader) { return Lwjgl3ifyTextInput.supported(); }
    public boolean nativeInputAvailable(ClassLoader loader) { return Lwjgl3ifyTextInput.available(); }
    public SceneNativeTextInput.Port nativeInput(ClassLoader loader) {
        return Lwjgl3ifyTextInput.available() ? new Lwjgl3ifyTextInput() : null;
    }
}
