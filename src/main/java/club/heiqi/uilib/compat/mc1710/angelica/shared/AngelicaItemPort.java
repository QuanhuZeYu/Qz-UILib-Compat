package club.heiqi.uilib.compat.mc1710.angelica.shared;

import java.lang.reflect.Method;
import club.heiqi.uilib.host.client.image.ItemStateScope;

/** 保存Angelica缓存栈及Iris上下文，不改变UILib物品绘制模型。 */
public final class AngelicaItemPort extends ItemStateScope.Port {
    public AngelicaItemPort(ClassLoader loader) {
        super(load(loader));
    }
    private static Class<?> load(ClassLoader loader) {
        try { return Class.forName("com.gtnewhorizons.angelica.glsm.GLStateManager", false, loader); }
        catch (ClassNotFoundException error) { throw new IllegalStateException(error); }
    }
    @Override public int clientDepth() {

            try {
                Method ctx = type.getDeclaredMethod("ctx");
                ctx.setAccessible(true);
                Object context = ctx.invoke(null);
                java.lang.reflect.Field depth =
                        context.getClass().getDeclaredField("clientAttribStackPointer");
                depth.setAccessible(true);
                return depth.getInt(context);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("无法读取有效client attrib栈", error);
            }
        }

    @Override public float[] normal() {
            try {
                Class<?> shader =
                        Class.forName(
                                "com.gtnewhorizons.angelica.glsm.ffp.ShaderManager",
                                false,
                                type.getClassLoader());
                Object n = shader.getMethod("getCurrentNormal").invoke(null);
                return new float[] {
                    n.getClass().getField("x").getFloat(n),
                    n.getClass().getField("y").getFloat(n),
                    n.getClass().getField("z").getFloat(n)
                };
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("无法读取有效normal", error);
            }
        }


    /** Iris item/entity/block uniform 跟随有效宿主上下文，不能从 driver GL 推导。 */
    public static final class Captured {
        final Object state;
        final int block, entity, item;
        final Method setBlock, setEntity;

        Captured(Class<?> type) throws ReflectiveOperationException {
            state = type.getField("INSTANCE").get(null);
            block =
                    ((Number) type.getMethod("getCurrentRenderedBlockEntity").invoke(state))
                            .intValue();
            entity = ((Number) type.getMethod("getCurrentRenderedEntity").invoke(state)).intValue();
            item = ((Number) type.getMethod("getCurrentRenderedItem").invoke(state)).intValue();
            setBlock = type.getMethod("setCurrentBlockEntity", int.class);
            setEntity = type.getMethod("setCurrentEntityAndItem", int.class, int.class);
        }

        public static Captured read(ClassLoader loader) {
            try {
                return new Captured(
                        Class.forName(
                                "net.coderbot.iris.uniforms.CapturedRenderingState",
                                false,
                                loader));
            } catch (ClassNotFoundException absent) {
                return null;
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("无法保存Iris物品上下文", error);
            }
        }

        public void restore() {
            try {
                setEntity.invoke(state, entity, item);
                setBlock.invoke(state, block);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("无法归还Iris物品上下文", error);
            }
        }
    }


}
