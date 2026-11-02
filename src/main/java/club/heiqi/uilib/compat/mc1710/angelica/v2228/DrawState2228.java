package club.heiqi.uilib.compat.mc1710.angelica.v2228;

import club.heiqi.uilib.compat.mc1710.angelica.shared.ContextDrawState;

/** 2.2.28的preDraw仍在GLStateManager，其余draw/VAO事实随当前GLContextState。 */
public final class DrawState2228 extends ContextDrawState {
    public DrawState2228(ClassLoader loader) throws ReflectiveOperationException {
        super(loader, GlsmEntries2228.LOOKUP);
    }
}
