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

/** Iris pack program与Pass.use真实scope；不生成任何quad、不调用Tessellator。
 * producer必须使用自己的VAO/VBO，并依据requiredAttributes给完整vertex ABI。
 * shaderpack标准sampler2D要求STRAIGHT_RGBA采样view，不能重解释共享premult页。
 * 若无world pipeline返回null；存在且ABI错误明确失败。scope只在有效宿主GL线程短期使用。 */
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

    /** prepare选择Atlas view前只读查询，不改inputs/pass/program/FBO。null表示没有Iris world pass。 */
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
        // 成员跨scope复用，当前pass的输入、phase和program仍逐次读取。
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
            // Pass.use 按实际 program 的 draw buffers 和 before/after 阶段绑定目标。
            // 历史 caller 可以使用另一 FBO；保留 Iris 的选择，不以句柄相同误拒绝合法回放。
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
        } catch (ReflectiveOperationException | RuntimeException error) {
            try {
                producer.close();
            } catch (RuntimeException cleanup) {
                error.addSuppressed(cleanup);
            }
            try {
                restore();
            } catch (ReflectiveOperationException restoreFailure) {
                error.addSuppressed(restoreFailure);
            }
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

    /** 来源时间冻结的FFP generic属性；延期回放不会读取当前mesh属性。 */
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

    /** 在任何raw producer绑定前调用；pointer注册及enable必须随后使用返回scope。 */
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

    /** typedattrs来自实际active属性匹配，遗漏/未知必须由producer明确拒绝，不能补猜测默认值。 */
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

    /** producer完成VAO/effective flags同步后调用，刷新当前宿主兼容uniform/扩展属性。
     * 不代替producer设置midUV/entity/tangent，不能只凭此就宣称全pack ABI通过。 */
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

    private void restore() throws ReflectiveOperationException {
        setPhase.invoke(pipeline, oldPhase);
        setTranslucency.invoke(pipeline, oldTranslucency);
        setInputs.invoke(pipeline, oldInputs);
        rebind.invoke(pipeline);
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            try {
                producer.close();
            } finally {
                try {
                    restore();
                    int restored =
                            ((Number)
                                            getProgram.invoke(pipeline))
                                    .intValue();
                    if (restored > 0) drawState.refreshUniforms(restored);
                } catch (ReflectiveOperationException error) {
                    throw new IllegalStateException("Iris pass scope恢复失败", error);
                }
            }
        }
    }
}
