package club.heiqi.uilib.compat.mc1710.angelica.shared;

import club.heiqi.uilib.host.client.gl.GlApi;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.FloatBuffer;

/** 同一 provider 复用稳定成员元数据，短 scope 按次读取 pipeline 与 GL 状态。
 * 查询、world 类型和活动 scope 分阶段绑定，producer 依赖随活动 scope 加载。 */
final class IrisReflectionBindings {
    private final ClassLoader loader;
    private final Method getManager;
    private Manager manager;
    private World world;

    IrisReflectionBindings(ClassLoader loader) throws ReflectiveOperationException {
        this.loader = loader;
        Class<?> iris = Class.forName("net.coderbot.iris.Iris", false, loader);
        getManager = iris.getMethod("getPipelineManagerNullable");
    }

    Object pipeline() throws ReflectiveOperationException {
        Object current = getManager.invoke(null);
        if (current == null) return null;
        Manager binding = manager;
        if (binding == null || binding.type != current.getClass()) {
            binding = new Manager(current.getClass());
            manager = binding;
        }
        return binding.getPipeline.invoke(current);
    }

    World world() throws ReflectiveOperationException {
        if (world == null) world = new World(loader);
        return world;
    }

    private static final class Manager {
        final Class<?> type;
        final Method getPipeline;

        Manager(Class<?> type) throws ReflectiveOperationException {
            this.type = type;
            getPipeline = type.getMethod("getPipelineNullable");
        }
    }

    static final class World {
        final Class<?> type;
        final Method shouldOverride;
        private Scope scope;

        World(ClassLoader loader) throws ReflectiveOperationException {
            type = Class.forName("net.coderbot.iris.pipeline.DeferredWorldRenderingPipeline", false, loader);
            shouldOverride = type.getMethod("shouldOverrideShaders");
        }

        Scope scope(ClassLoader loader, GlApi gl) throws ReflectiveOperationException {
            if (scope == null) scope = new Scope(loader, gl, type);
            return scope;
        }
    }

    static final class Scope {
        final Method setInputs, getPhase, setPhase, setTranslucency, rebind, getProgram;
        final Field inputs, translucent;
        final IrisInputAvailability inputAvailability;
        final Producer producer;
        final HostProgramSnapshot.Capture snapshots;

        Scope(ClassLoader loader, GlApi gl, Class<?> type) throws ReflectiveOperationException {
            Class<?> inputsType = Class.forName("net.coderbot.iris.gbuffer_overrides.matching.InputAvailability", false, loader);
            inputAvailability = new IrisInputAvailability(inputsType);
            setInputs = type.getMethod("setInputs", inputsType);
            getPhase = type.getMethod("getPhase");
            setPhase = type.getMethod("setPhase", getPhase.getReturnType());
            setTranslucency = type.getMethod("setDeclaredTranslucency", Boolean.class);
            rebind = type.getMethod("rebindCurrentPass");
            getProgram = type.getMethod("getActivePassProgramId");
            inputs = type.getDeclaredField("inputs");
            inputs.setAccessible(true);
            translucent = type.getDeclaredField("declaredTranslucent");
            translucent.setAccessible(true);
            producer = new Producer(loader);
            snapshots = new HostProgramSnapshot.Capture(loader, gl);
        }
    }

    static final class Producer {
        final Method getFloat, getInteger, matrixMode, loadMatrix, bindVao, bindBuffer,
                pointer, integerPointer, enable, disable, setFlags, activeTexture, bindTexture,
                color, normalSetter, getNormal, blockSetter, entitySetter, getBlock, getEntity, getItem;
        final Field positionBit, colorBit, textureBit, normalBit, brightnessBit, captured;
        private Normal normal;

        Producer(ClassLoader loader) throws ReflectiveOperationException {
            Class<?> glsm = Class.forName("com.gtnewhorizons.angelica.glsm.GLStateManager", false, loader);
            getFloat = glsm.getMethod("glGetFloat", int.class, FloatBuffer.class);
            getInteger = glsm.getMethod("glGetInteger", int.class);
            matrixMode = glsm.getMethod("glMatrixMode", int.class);
            loadMatrix = glsm.getMethod("glLoadMatrix", FloatBuffer.class);
            bindVao = glsm.getMethod("glBindVertexArray", int.class);
            bindBuffer = glsm.getMethod("glBindBuffer", int.class, int.class);
            pointer = glsm.getMethod("glVertexAttribPointer", int.class, int.class, int.class,
                    boolean.class, int.class, long.class);
            integerPointer = glsm.getMethod("glVertexAttribIPointer", int.class, int.class, int.class,
                    int.class, long.class);
            enable = glsm.getMethod("glEnableVertexAttribArray", int.class);
            disable = glsm.getMethod("glDisableVertexAttribArray", int.class);
            bindTexture = glsm.getMethod("glBindTexture", int.class, int.class);
            activeTexture = glsm.getMethod("glActiveTexture", int.class);
            color = glsm.getMethod("glColor4f", float.class, float.class, float.class, float.class);
            normalSetter = glsm.getMethod("glNormal3f", float.class, float.class, float.class);
            Class<?> vao = Class.forName("com.gtnewhorizons.angelica.glsm.ffp.VAOManager", false, loader);
            setFlags = vao.getMethod("setCurrentVertexFlags", int.class);
            Class<?> flags = Class.forName("com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags", false, loader);
            positionBit = flags.getField("POSITION_BIT");
            colorBit = flags.getField("COLOR_BIT");
            textureBit = flags.getField("TEXTURE_BIT");
            normalBit = flags.getField("NORMAL_BIT");
            brightnessBit = flags.getField("BRIGHTNESS_BIT");
            Class<?> shader = Class.forName("com.gtnewhorizons.angelica.glsm.ffp.ShaderManager", false, loader);
            getNormal = shader.getMethod("getCurrentNormal");
            Class<?> state = Class.forName("net.coderbot.iris.uniforms.CapturedRenderingState", false, loader);
            captured = state.getField("INSTANCE");
            blockSetter = state.getMethod("setCurrentBlockEntity", int.class);
            entitySetter = state.getMethod("setCurrentEntityAndItem", int.class, int.class);
            getBlock = state.getMethod("getCurrentRenderedBlockEntity");
            getEntity = state.getMethod("getCurrentRenderedEntity");
            getItem = state.getMethod("getCurrentRenderedItem");
        }

        float[] normal() throws ReflectiveOperationException {
            Object current = getNormal.invoke(null);
            Normal binding = normal;
            // 字段访问器按实际类型复用，normal 对象和值仍属于当前 scope。
            if (binding == null || binding.type != current.getClass()) {
                binding = new Normal(current.getClass());
                normal = binding;
            }
            return new float[] {binding.x.getFloat(current), binding.y.getFloat(current), binding.z.getFloat(current)};
        }
    }

    private static final class Normal {
        final Class<?> type;
        final Field x, y, z;

        Normal(Class<?> type) throws ReflectiveOperationException {
            this.type = type;
            x = type.getField("x");
            y = type.getField("y");
            z = type.getField("z");
        }
    }
}
