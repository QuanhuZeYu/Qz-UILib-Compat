package club.heiqi.uilib.compat.mc1710.angelica.shared;

import club.heiqi.uilib.host.client.gl.GlApi;
import club.heiqi.uilib.host.client.font.compat.CompatBuffers;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.FloatBuffer;

/** 保存延期计划的 pipeline 身份、pack 代际、phase 与来源值；program 和 UV 在回放时取得。
 * pipeline 或 pack 换代时拒绝旧计划，确保回放使用当前存活的 FBO/program。 */
final class HostProgramSnapshot {
    private static final ThreadLocal<HostProgramSnapshot> CURRENT = new ThreadLocal<>();
    final Object pipeline, phase;
    final int generation;
    final boolean active;
    final int entityId, itemId;
    final float[] lightmapMatrix;
    final SourceExtendedAttributes extended;
    private final Capture source;

    private HostProgramSnapshot(Capture source, Object pipeline, Object phase, int generation,
            boolean active, int entityId, int itemId, float[] lightmapMatrix,
            SourceExtendedAttributes extended) {
        this.source = source;
        this.pipeline = pipeline;
        this.phase = phase;
        this.generation = generation;
        this.active = active;
        this.entityId = entityId;
        this.itemId = itemId;
        this.lightmapMatrix = lightmapMatrix.clone();
        this.extended = extended;
    }

    /** bridge/provider 复用稳定成员元数据，manager、纹理状态与渲染对象在每次 capture 时读取。
     * class 身份变化时重绑对应访问器，保持来源事实随宿主更新。 */
    static final class Capture {
        private final ClassLoader loader;
        private final GlApi gl;
        private Method getManager, getGeneration, getPipeline, shouldOverride, getPhase;
        private Method getEntity, getItem, getTextures, getLightmap, getMatrix;
        private Field capturedInstance;
        private Class<?> deferredType, managerType, texturesType, matrixType;

        Capture(ClassLoader loader, GlApi gl) {
            this.loader = loader;
            this.gl = gl;
        }

        private void bindIdentity() throws ReflectiveOperationException {
            if (getManager != null) return;
            Class<?> iris = Class.forName("net.coderbot.iris.Iris", false, loader);
            Method manager = iris.getMethod("getPipelineManagerNullable");
            Method generation = iris.getMethod("getShaderPackLoadId");
            getGeneration = generation;
            getManager = manager;
        }

        private Object pipeline() throws ReflectiveOperationException {
            Object manager = getManager.invoke(null);
            if (manager == null) return null;
            if (managerType != manager.getClass()) {
                Method getter = manager.getClass().getMethod("getPipelineNullable");
                getPipeline = getter;
                managerType = manager.getClass();
            }
            return getPipeline.invoke(manager);
        }

        private void bindDeferred() throws ReflectiveOperationException {
            if (deferredType != null) return;
            Class<?> type = Class.forName("net.coderbot.iris.pipeline.DeferredWorldRenderingPipeline", false, loader);
            Method override = type.getMethod("shouldOverrideShaders");
            Method phase = type.getMethod("getPhase");
            shouldOverride = override;
            getPhase = phase;
            deferredType = type;
        }

        private void bindActive() throws ReflectiveOperationException {
            if (capturedInstance != null) return;
            Class<?> captured = Class.forName("net.coderbot.iris.uniforms.CapturedRenderingState", false, loader);
            Field instance = captured.getField("INSTANCE");
            Method entity = captured.getMethod("getCurrentRenderedEntity");
            Method item = captured.getMethod("getCurrentRenderedItem");
            Class<?> glsm = Class.forName("com.gtnewhorizons.angelica.glsm.GLStateManager", false, loader);
            Method textures = glsm.getMethod("getTextures");
            getEntity = entity;
            getItem = item;
            getTextures = textures;
            capturedInstance = instance;
        }

        private float[] lightmap() throws ReflectiveOperationException {
            Object textures = getTextures.invoke(null);
            if (texturesType != textures.getClass()) {
                Method getter = textures.getClass().getMethod("getTextureUnitMatrix", int.class);
                getLightmap = getter;
                texturesType = textures.getClass();
            }
            Object matrix = getLightmap.invoke(textures, 1);
            if (matrixType != matrix.getClass()) {
                Method getter = matrix.getClass().getMethod("get", FloatBuffer.class);
                getMatrix = getter;
                matrixType = matrix.getClass();
            }
            FloatBuffer buffer = CompatBuffers.floats(16);
            getMatrix.invoke(matrix, buffer);
            float[] value = new float[16];
            for (int i = 0; i < value.length; i++) value[i] = buffer.get(i);
            return value;
        }

        HostProgramSnapshot capture() {
            try {
                bindIdentity();
                Object pipeline = pipeline();
                int generation = ((Number) getGeneration.invoke(null)).intValue();
                bindDeferred();
                boolean active = deferredType.isInstance(pipeline)
                        && (Boolean) shouldOverride.invoke(pipeline);
                Object phase = active ? getPhase.invoke(pipeline) : null;
                int entity = 0, item = 0;
                float[] lightmap;
                if (active) {
                    bindActive();
                    Object captured = capturedInstance.get(null);
                    entity = ((Number) getEntity.invoke(captured)).intValue();
                    item = ((Number) getItem.invoke(captured)).intValue();
                    lightmap = lightmap();
                } else {
                    lightmap = new float[16];
                    lightmap[0] = lightmap[5] = lightmap[10] = lightmap[15] = 1;
                }
                return new HostProgramSnapshot(this, pipeline, phase, generation, active,
                        entity, item, lightmap, active ? SourceExtendedAttributes.capture(gl) : null);
            } catch (ClassNotFoundException absent) {
                if ("net.coderbot.iris.Iris".equals(absent.getMessage())) return null;
                throw new IllegalStateException("Iris存在但必需ABI类缺失", absent);
            } catch (ReflectiveOperationException | LinkageError error) {
                throw new IllegalStateException("Iris来源snapshot失败", error);
            }
        }

        private void validate(HostProgramSnapshot snapshot) {
            try {
                // 回放验证 pipeline 与 pack 的有效性；矩阵和 extended 属性继续使用已捕获的来源值。
                if (pipeline() != snapshot.pipeline
                        || ((Number) getGeneration.invoke(null)).intValue() != snapshot.generation)
                    throw new IllegalStateException(
                            "延期font来源Iris pipeline/pack已失效，请丢弃旧pass而非绘制到新program");
            } catch (ReflectiveOperationException | LinkageError error) {
                throw new IllegalStateException("Iris来源snapshot验证失败", error);
            }
        }
    }

    static HostProgramSnapshot capture(ClassLoader loader, GlApi gl) {
        return new Capture(loader, gl).capture();
    }

    static HostProgramSnapshot current() { return CURRENT.get(); }

    static void replay(HostProgramSnapshot captured, Runnable draw) {
        HostProgramSnapshot previous = CURRENT.get();
        if (captured != null) captured.source.validate(captured);
        CURRENT.set(captured);
        try {
            draw.run();
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }
}
