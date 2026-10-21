package club.heiqi.uilib.compat.mixin.mc1710.ae2.rv3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import club.heiqi.uilib.host.client.font.VanillaFontOptions;
import club.heiqi.uilib.host.client.font.MinecraftFontServices;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

/** #69：monitor缓存含字体来源；开关变化必须重编译，不能混播旧字符与逻辑PaintPlan。
 * 保留AE2六向变换、数量更新和COMPILE_AND_EXECUTE，逻辑文字由Angelica按当前矩阵回放。 */
@Pseudo
@Mixin(targets = "appeng.parts.reporting.AbstractPartMonitor", remap = false)
public abstract class MonitorFontCacheMixin {
    @Shadow private boolean updateList;
    @Unique private boolean qz$fontStateKnown;
    @Unique private boolean qz$fontEnabled;
    @Unique private long qz$fontRevision;
    @Unique private boolean qz$direct;
    @Unique private static final boolean qz$logicalLists =
            MonitorFontCacheMixin.class.getClassLoader().getResource(
                    "com/gtnewhorizons/angelica/glsm/DisplayListManager.class") != null;

    @Inject(method = "renderDynamic(DDDLappeng/api/parts/IPartRenderHelper;Lnet/minecraft/client/renderer/RenderBlocks;)V",
            at = @At("HEAD"), require = 1)
    private void qz$invalidateFontCache(CallbackInfo callback) {
        boolean enabled = VanillaFontOptions.enabled();
        long revision = MinecraftFontServices.fontGeneration();
        qz$direct = enabled && !qz$logicalLists;
        if (qz$direct || !qz$fontStateKnown || enabled != qz$fontEnabled || revision != qz$fontRevision) {
            updateList = true;
            qz$fontEnabled = enabled;
            qz$fontRevision = revision;
            qz$fontStateKnown = true;
        }
    }

    // 原生GL列表不能保存VAO/uniform或未来字形；只让原AE2绘制体逐帧执行，不重写其坐标和业务。
    @WrapOperation(method = "renderDynamic(DDDLappeng/api/parts/IPartRenderHelper;Lnet/minecraft/client/renderer/RenderBlocks;)V",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glNewList(II)V"), require = 1)
    private void qz$beginList(int id, int mode, Operation<Void> original) {
        if (!qz$direct) original.call(id, mode);
    }
    @WrapOperation(method = "renderDynamic(DDDLappeng/api/parts/IPartRenderHelper;Lnet/minecraft/client/renderer/RenderBlocks;)V",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glEndList()V"), require = 1)
    private void qz$endList(Operation<Void> original) {
        if (!qz$direct) original.call();
    }
}
