package club.heiqi.uilib.compat.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import club.heiqi.uilib.compat.mc1710.AngelicaAbi;
import club.heiqi.uilib.compat.mc1710.lwjgl3ify.sdl.SceneNativeInputSupport;

/** 根据 class 资源选择 Mixin，目标类的加载归 Mixin 阶段。
 * UniMixins 将 ASM 包重映射至运行时 ABI；单一 ClassNode 签名保持重映射后的方法签名唯一。 */
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
