package club.heiqi.uilib.compat.mc1710.angelica.v2243;

import club.heiqi.uilib.compat.mc1710.angelica.shared.ContextDrawState;

/** 2.2.40+把preDraw移到GLSMDraws，其余draw/VAO事实仍随当前GLContextState。 */
public final class DrawState2243 extends ContextDrawState {
    public DrawState2243(ClassLoader loader) throws ReflectiveOperationException {
        super(loader, GlsmEntries2243.LOOKUP);
    }
}
