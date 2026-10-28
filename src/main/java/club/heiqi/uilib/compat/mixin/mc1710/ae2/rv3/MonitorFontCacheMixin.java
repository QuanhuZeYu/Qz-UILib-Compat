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

/** #69：monitor 缓存随字体开关和排版代际重建，使原版字符与逻辑 PaintPlan 各自使用当前来源。
 * AE2 保留六向变换、数量更新和 COMPILE_AND_EXECUTE，逻辑文字由 Angelica 按当前矩阵回放。 */
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

    // 字体替换开启且 Angelica 缺席时逐帧执行原 AE2 绘制体，使 VAO、uniform 和后续字形在当前帧生效。
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
