package club.heiqi.uilib.compat.mc1710.angelica.shared;

import club.heiqi.uilib.host.client.font.compat.*;
import club.heiqi.uilib.host.client.font.context.*;
import club.heiqi.uilib.host.client.gl.*;

import club.heiqi.uilib.host.client.gl.GlApi;
import club.heiqi.uilib.host.client.gl.GlProgramScope;
import club.heiqi.uilib.host.client.gl.GlProgramAttribute;
import club.heiqi.uilib.host.client.gl.GlTokens;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import club.heiqi.uilib.host.client.font.context.FontRenderContext;

/** 在宿主 GL 线程短期管理 Iris pack program 与 Pass.use，几何由自有 producer 提供。
 * producer 使用自有 VAO/VBO，并按 requiredAttributes 提供完整 vertex ABI。
 * shaderpack 的标准 sampler2D 使用 STRAIGHT_RGBA view，共享 premult 页保留原采样语义。
 * world pipeline 缺席时返回 null，宿主存在且 ABI 失配时明确失败。 */
public final class OptionalIrisProgramScope implements GlProgramScope {
    public enum SamplingView {
        STRAIGHT_RGBA
    }

    public static final class Attribute {
        public final String name;
        public final int location, type, size;

        Attribute(String name, int location, int type, int size) {
            this.name = name;
            this.location = location;
            this.type = type;
            this.size = size;
        }
    }

    public final int programId;
    public final SamplingView samplingView = SamplingView.STRAIGHT_RGBA;
    public final int packedLight;
    private final float[] normal;
    private final boolean lightmapActive;
    public final List<Attribute> requiredAttributes;
    private final List<GlProgramAttribute> programAttributes;
    private final Object pipeline, oldInputs, oldPhase, oldTranslucency;
    private final Method setInputs, setPhase, setTranslucency, rebind, getProgram;
    private final GlApi gl;
    private boolean closed;
    private final ProducerStateScope producer;
    public final int entityId, itemId, blockEntityId;
    private final SourceExtendedAttributes sourceExtended;
    private final DrawState drawState;

    /** prepare 选择 atlas view 前读取活动 pass，保留 inputs/pass/program/FBO；null 表示 Iris world pass 缺席。 */
    public static SamplingView preview(ClassLoader loader, FontRenderContext context) {
        return preview(loader, context, null);
    }

    static SamplingView preview(
            ClassLoader loader, FontRenderContext context, IrisProgramProvider provider) {
        try {
            IrisReflectionBindings bindings = provider == null
                    ? new IrisReflectionBindings(loader) : provider.bindings();
            Object pipeline = bindings.pipeline();
            if (pipeline == null) return null;
            IrisReflectionBindings.World world = bindings.world();
            HostProgramSnapshot captured = HostProgramSnapshot.current();
            if (captured != null) return captured.active ? SamplingView.STRAIGHT_RGBA : null;
            return world.type.isInstance(pipeline)
                            && (Boolean) world.shouldOverride.invoke(pipeline)
                    ? SamplingView.STRAIGHT_RGBA
                    : null;
        } catch (ClassNotFoundException absent) {
            if ("net.coderbot.iris.Iris".equals(absent.getMessage())) return null;
            throw new IllegalStateException("Iris存在但必需ABI类缺失", absent);
        } catch (ReflectiveOperationException | LinkageError error) {
            throw new IllegalStateException("Iris采样view查询失败，未静默使用premult atlas", error);
        }
    }

    public static OptionalIrisProgramScope open(
            ClassLoader loader, GlApi gl, FontRenderContext context) {
        return open(loader, gl, context, null);
    }

    static OptionalIrisProgramScope open(
            ClassLoader loader, GlApi gl, FontRenderContext context, IrisProgramProvider provider) {
        HostProgramSnapshot source = HostProgramSnapshot.current();
        if (source != null && !source.active) return null;
        try {
            IrisReflectionBindings bindings = provider == null
                    ? new IrisReflectionBindings(loader) : provider.bindings();
            Object pipeline = bindings.pipeline();
            if (pipeline == null) return null;
            IrisReflectionBindings.World world = bindings.world();
            if (!world.type.isInstance(pipeline)) return null;
            if (!(Boolean) world.shouldOverride.invoke(pipeline)) {
                HostProgramSnapshot captured = HostProgramSnapshot.current();
                if (captured != null && captured.active)
                    throw new IllegalStateException("延期Iris文字回放已离开有效world pass，禁止own shader回退");
                return null;
            }
            DrawState drawState = provider == null ? DrawState.bind(loader) : provider.drawState();
            return new OptionalIrisProgramScope(
                    pipeline, gl, context, drawState, world.scope(loader, gl));
        } catch (ClassNotFoundException absent) {
            if ("net.coderbot.iris.Iris".equals(absent.getMessage())) return null;
            throw new IllegalStateException("Iris存在但必需ABI类缺失", absent);
        } catch (ReflectiveOperationException | LinkageError error) {
            throw new IllegalStateException("Iris world program接口失配，禁止own shader伪兼容", error);
        }
    }

    private OptionalIrisProgramScope(
            Object pipeline, GlApi gl, FontRenderContext context, DrawState drawState,
            IrisReflectionBindings.Scope bindings) throws ReflectiveOperationException {
        this.gl = java.util.Objects.requireNonNull(gl, "gl");
        this.pipeline = pipeline;
        IrisInputAvailability inputAvailability = bindings.inputAvailability;
        setInputs = bindings.setInputs;
        Method getPhase = bindings.getPhase;
        setPhase = bindings.setPhase;
        setTranslucency = bindings.setTranslucency;
        rebind = bindings.rebind;
        getProgram = bindings.getProgram;
        // 成员跨 scope 复用，当前 pass 的输入、phase 和 program 仍逐次读取。
        oldInputs = bindings.inputs.get(pipeline);
        oldTranslucency = bindings.translucent.get(pipeline);
        oldPhase = getPhase.invoke(pipeline);
        this.drawState = drawState;
        packedLight = context.state.packedLight;
        normal = context.state.normal();
        lightmapActive = context.state.lightmapActive;
        HostProgramSnapshot source = HostProgramSnapshot.current();
        if (source == null) source = bindings.snapshots.capture();
        sourceExtended = source == null ? null : source.extended;
        entityId = source == null ? 0 : source.entityId;
        itemId = source == null ? 0 : source.itemId;
        blockEntityId = context.state.blockEntityId;
        producer = new ProducerStateScope(gl, context, source, drawState, bindings.producer);
        try {
            HostProgramSnapshot captured = HostProgramSnapshot.current();
            if (captured != null && captured.active) setPhase.invoke(pipeline, captured.phase);
            setTranslucency.invoke(pipeline, Boolean.TRUE);
            setInputs.invoke(
                    pipeline, inputAvailability.create(true, context.state.lightmapActive));
            rebind.invoke(pipeline);
            programId =
                    ((Number) getProgram.invoke(pipeline)).intValue();
            if (programId <= 0) throw new IllegalStateException("Iris当前font pass没有可提交program");
            // Pass.use 按实际 program 的 draw buffers 与 before/after 阶段绑定目标。
            // 历史 caller 可使用另一 FBO，回放目标遵循 Iris 当前选择。
            List<Attribute> attributes = new ArrayList<Attribute>();
            int count = gl.glGetProgrami(programId, GlTokens.GL_ACTIVE_ATTRIBUTES);
            for (int i = 0; i < count; i++) {
                GlProgramAttribute attribute = gl.glGetActiveAttribute(programId, i);
                attributes.add(
                        new Attribute(
                                attribute.name,
                                attribute.location,
                                attribute.type,
                                attribute.size));
            }
            requiredAttributes = Collections.unmodifiableList(attributes);
            List<GlProgramAttribute> exposed = new ArrayList<GlProgramAttribute>();
            for (Attribute a : attributes)
                exposed.add(new GlProgramAttribute(a.name, a.location, a.type, a.size));
            programAttributes = Collections.unmodifiableList(exposed);
        } catch (ReflectiveOperationException | RuntimeException | Error error) {
            try {
                producer.close();
            } catch (RuntimeException | Error cleanup) {
                cleanupFailure(error, cleanup);
            }
            restore(error);
            throw error;
        }
    }

    @Override
    public int programId() {
        return programId;
    }

    @Override
    public int packedLight() {
        return packedLight;
    }

    @Override
    public List<GlProgramAttribute> attributes() {
        return programAttributes;
    }

    @Override
    public float[] extended(String name, int type) {
        return freezeSourceExtendedAttributes().valuesFor(name, type);
    }

    @Override
    public void configure(
            int vao,
            int vbo,
            int ebo,
            boolean uv,
            boolean color,
            boolean lightmap,
            boolean normal) {
        configureProducer(vao, vbo, ebo, uv, color, lightmap, normal);
    }

    @Override
    public void pointer(
            int location, int size, int type, boolean normalized, int stride, long offset) {
        producer.pointer(location, size, type, normalized, stride, offset);
    }

    @Override
    public void disable(int location) {
        producer.disable(location);
    }

    public float[] normal() {
        return normal.clone();
    }

    /** 返回来源时刻冻结的 FFP generic 属性，延期回放继续使用该快照。 */
    public SourceExtendedAttributes freezeSourceExtendedAttributes() {
        if (sourceExtended == null) throw new IllegalStateException("缺少来源FFP extended属性");
        return sourceExtended;
    }

    public float[] entityAttribute() {
        return freezeSourceExtendedAttributes().mcEntity();
    }

    public float[] midBlockAttribute() {
        return freezeSourceExtendedAttributes().midBlock();
    }

    public void bindTexture2D(int texture) {
        if (closed) throw new IllegalStateException("Iris scope已关闭");
        producer.bindTexture2D(texture);
    }

    /** 在首次 raw producer 绑定前调用，随后通过返回的 scope 注册 pointer 与 enable，保持宿主缓存同步。 */
    public ProducerStateScope configureProducer(
            int vao,
            int vbo,
            int ebo,
            boolean uv,
            boolean color,
            boolean lightmap,
            boolean normal) {
        if (closed) throw new IllegalStateException("Iris scope已关闭");
        producer.configure(vao, vbo, ebo, uv, color, lightmap, normal);
        return producer;
    }

    /** producer 按活动属性校验类型与语义并提供 typed 列表；此入口核对 location 覆盖，缺失时明确失败。 */
    public void bindProducerAttributes(
            int vao,
            int vbo,
            int ebo,
            java.util.Collection<ProducerStateScope.VertexAttribute> attributes) {
        if (attributes == null) throw new NullPointerException("attributes");
        for (Attribute required : requiredAttributes) {
            boolean found = false;
            for (ProducerStateScope.VertexAttribute supplied : attributes)
                if (supplied.location == required.location) {
                    found = true;
                    break;
                }
            if (!found) throw new IllegalStateException("Iris producer缺少属性：" + required.name);
        }
        configureProducer(vao, vbo, ebo, true, true, lightmapActive, true);
        for (ProducerStateScope.VertexAttribute attribute : attributes)
            producer.attribute(attribute);
    }

    /** producer 同步 VAO 与有效 flags 后调用，以宿主 preDraw 刷新兼容 uniform/扩展状态。
     * midUV/entity/tangent 由 producer 提供；完整 pack ABI 由实际活动属性与来源值共同验证。 */
    public void beforeDraw() {
        if (closed) throw new IllegalStateException("Iris program scope已关闭");
        try {
            drawState.beforeDraw(GlTokens.GL_TRIANGLES);
            if (gl.glGetInteger(GlTokens.GL_CURRENT_PROGRAM) != programId)
                throw new IllegalStateException("Iris preDraw改变了已匹配的pack program，拒绝错误vertex ABI提交");
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Iris draw prehook失败", error);
        }
    }

    private static Throwable cleanupFailure(Throwable failure, Throwable error) {
        if (error instanceof ReflectiveOperationException)
            error = new IllegalStateException("Iris pass scope恢复失败", error);
        if (failure == null) return error;
        if (failure != error) failure.addSuppressed(error);
        return failure;
    }

    private Throwable restore(Throwable failure) {
        // scope 逐项归还 pass 状态，单项失败后继续其余 setter 与最终 rebind，并收集清理错误。
        try { setPhase.invoke(pipeline, oldPhase); }
        catch (ReflectiveOperationException | RuntimeException | Error error) {
            failure = cleanupFailure(failure, error);
        }
        try { setTranslucency.invoke(pipeline, oldTranslucency); }
        catch (ReflectiveOperationException | RuntimeException | Error error) {
            failure = cleanupFailure(failure, error);
        }
        try { setInputs.invoke(pipeline, oldInputs); }
        catch (ReflectiveOperationException | RuntimeException | Error error) {
            failure = cleanupFailure(failure, error);
        }
        try { rebind.invoke(pipeline); }
        catch (ReflectiveOperationException | RuntimeException | Error error) {
            failure = cleanupFailure(failure, error);
        }
        return failure;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        Throwable failure = null;
        try { producer.close(); }
        catch (RuntimeException | Error error) { failure = error; }
        failure = restore(failure);
        try {
            int restored = ((Number) getProgram.invoke(pipeline)).intValue();
            if (restored > 0) drawState.refreshUniforms(restored);
        } catch (ReflectiveOperationException | RuntimeException | Error error) {
            failure = cleanupFailure(failure, error);
        }
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
        if (failure instanceof Error) throw (Error) failure;
    }
}
