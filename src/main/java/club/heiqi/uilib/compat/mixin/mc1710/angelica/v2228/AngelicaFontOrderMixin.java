package club.heiqi.uilib.compat.mixin.mc1710.angelica.v2228;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import club.heiqi.uilib.compat.mc1710.angelica.shared.AngelicaBridge;

/** 插件按 Angelica 资源与 ABI 安装钩子，require 校验注入点数量，失配时明确失败。
 * 旧 segment 的顺序与生命周期由此通知，主绘制由 UILib 共享 plan/backend 完成。 */
@Pseudo
@Mixin(targets = "com.gtnewhorizons.angelica.client.font.BatchingFontRenderer", remap = false)
public abstract class AngelicaFontOrderMixin {
    @Unique
    public boolean uilibFontOrderHooksInstalled() {
        return true;
    }

    @Inject(method = "sealBatchSegment()V", at = @At("RETURN"), require = 1)
    private void uilibSealed(CallbackInfo ci) {
        AngelicaBridge.sealed(this);
    }

    // WrapOperation 的 Injector.validateParams 支持同维引用数组 Coerce；CallbackInjector 的数组分支要求原类型。
    // 两个 renderer 都可能拥有 [0,1) 范围，保序通知携带实际 owner，并在原 helper 前执行。
    @WrapOperation(
            method = {"flushDeferredTextInner()V", "flushBatchInner()V"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/gtnewhorizons/angelica/client/font/BatchingFontRenderer;drawCommands([Lcom/gtnewhorizons/angelica/client/font/BatchingFontRenderer$FontDrawCmd;IILcom/gtnewhorizons/angelica/client/font/BatchingFontRenderer;)V"),
            require = 3)
    private static void uilibBeforeDirect(
            @Coerce Object[] commands,
            int from,
            int to,
            @Coerce Object owner,
            Operation<Void> original) {
        AngelicaBridge.beforeOldReplay(owner, from, to);
        original.call(commands, from, to, owner);
    }

    // 2.2.28 管线无返回值；回放前消费对应文字票据。
    @WrapOperation(
            method = {"flushDeferredThroughPipeline()V", "flushBatchThroughPipeline()V"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/gtnewhorizons/angelica/client/font/BatchingFontRenderer;emitRangeThroughPipeline(Lcom/gtnewhorizons/angelica/client/font/BatchingFontRenderer;[Lcom/gtnewhorizons/angelica/client/font/BatchingFontRenderer$FontDrawCmd;IIIFFF)V"),
            require = 3)
    private static void uilibBeforePipeline(
            @Coerce Object owner,
            @Coerce Object[] commands,
            int from,
            int to,
            int light,
            float nx,
            float ny,
            float nz,
            Operation<Void> original) {
        AngelicaBridge.beforeOldReplay(owner, from, to);
        original.call(owner, commands, from, to, light, nx, ny, nz);
    }

    @Inject(
            method = {"flushDeferredText()V", "flushBatch()V"},
            at = @At("HEAD"),
            require = 1)
    private static void uilibBeginFlush(CallbackInfo ci) {
        AngelicaBridge.beginFlush();
    }

    @Inject(
            method = {"flushDeferredText()V", "flushBatch()V"},
            at = @At("RETURN"),
            require = 1)
    private static void uilibEndFlush(CallbackInfo ci) {
        AngelicaBridge.endFlush();
    }

    @Inject(method = "discardDeferredText()V", at = @At("RETURN"), require = 1)
    private static void uilibDiscard(CallbackInfo ci) {
        AngelicaBridge.discarded();
    }

    @Inject(method = "endFrame()V", at = @At("HEAD"), require = 1)
    private static void uilibAbortFrame(CallbackInfo ci) {
        AngelicaBridge.frameEnded();
    }
}
