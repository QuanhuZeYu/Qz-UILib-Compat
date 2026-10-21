package club.heiqi.uilib.compat.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import club.heiqi.uilib.compat.mc1710.AngelicaAbi;
import club.heiqi.uilib.compat.mc1710.lwjgl3ify.sdl.SceneNativeInputSupport;

/** 仅读取资源选择版本，Mixin目标不能在插件选择阶段Class.forName。
 * UniMixins会将ASM包重映射至运行时ABI；只能声明一个ClassNode版本，否则映射后方法重名。 */
public final class CompatMixinPlugin implements IMixinConfigPlugin {
    public void onLoad(String mixinPackage) {}
    public String getRefMapperConfig() { return null; }
    public boolean shouldApplyMixin(String target, String mixin) {
        ClassLoader loader = getClass().getClassLoader();
        if (loader.getResource(target.replace('.', '/') + ".class") == null) return false;
        if (mixin.contains(".angelica.")) {
            String abi = AngelicaAbi.select(loader);
            return abi != null && mixin.contains("." + abi + ".");
        }
        if (mixin.contains(".ae2.")) {
            try {
                if (!SceneNativeInputSupport.member(loader, target.replace('.', '/') + ".class", false, "updateList", "Z", 0))
                    throw new IllegalStateException("AE2 monitor缺少updateList缓存失效合同");
                return true;
            } catch (java.io.IOException error) { throw new IllegalStateException(error); }
        }
        return false;
    }
    public void acceptTargets(Set<String> own, Set<String> others) {}
    public List<String> getMixins() { return null; }
    public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
