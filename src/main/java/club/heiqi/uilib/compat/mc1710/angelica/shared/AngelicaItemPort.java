package club.heiqi.uilib.compat.mc1710.angelica.shared;

import java.lang.reflect.Method;
import club.heiqi.uilib.host.client.image.ItemStateScope;

/** 为 UILib 物品绘制保存并恢复 Angelica 缓存栈与 Iris 上下文。 */
public final class AngelicaItemPort extends ItemStateScope.Port {
    private final club.heiqi.uilib.compat.mc1710.AngelicaAbi.StateProfile abi;

    public AngelicaItemPort(ClassLoader loader) {
        this(load(loader));
    }
    private AngelicaItemPort(club.heiqi.uilib.compat.mc1710.AngelicaAbi.StateProfile abi) {
        // 父 Port 的 active/clientActive 直接使用 multitexture；其余操作由 call 按同一 profile 路由。
        super(abi.textures);
        this.abi = abi;
    }
    private static club.heiqi.uilib.compat.mc1710.AngelicaAbi.StateProfile load(ClassLoader loader) {
        try { return club.heiqi.uilib.compat.mc1710.AngelicaAbi.states(
                Class.forName("com.gtnewhorizons.angelica.glsm.GLStateManager", false, loader)); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException("Angelica物品状态ABI不匹配", error); }
    }

    @Override protected Object call(String name, Class<?>[] signature, Object... args) {
        try { return abi.method(name, signature).invoke(null, args); }
        catch (java.lang.reflect.InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException("Angelica物品状态操作失败: " + name, cause);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Angelica物品状态入口不匹配: " + name, error);
        }
    }
    @Override protected int integer(int name) {
        // GLSM 模拟纹理与矩阵集合按宿主 MAX_TEXTURE_UNITS 分配，driver 的跨阶段 combined 上限属于另一能力范围。
        if (name == 34018 || name == 35661) {
            try { return abi.glStateManager.getField("MAX_TEXTURE_UNITS").getInt(null); }
            catch (ReflectiveOperationException error) {
                throw new IllegalStateException("无法读取 Angelica 物品纹理单元能力", error);
            }
        }
        return super.integer(name);
    }

    @Override public int clientDepth() {

            try {
                Method ctx = abi.glStateManager.getDeclaredMethod("ctx");
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


    /** 从有效宿主上下文保存并恢复 Iris 的 item/entity/block uniform 来源。 */
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
