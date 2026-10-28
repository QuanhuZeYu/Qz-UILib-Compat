package club.heiqi.uilib.compat.mc1710.angelica.shared;

import club.heiqi.uilib.host.client.font.compat.*;
import club.heiqi.uilib.host.client.font.context.*;
import club.heiqi.uilib.host.client.gl.*;

import java.util.Objects;
import club.heiqi.uilib.host.client.gl.GlApi;
import club.heiqi.uilib.host.client.gl.GlProgramProvider;
import club.heiqi.uilib.host.client.gl.GlProgramScope;
import club.heiqi.uilib.compat.mc1710.angelica.shared.GlFontIrisProducer;
import club.heiqi.uilib.host.client.gl.GlVertexProducer;
import club.heiqi.uilib.host.client.font.context.FontRenderContext;

/** 为 Minecraft 宿主提供可选 Iris 接入，GL binding 由宿主注入；缺席查询返回 false，活动 pass 才创建 scope。 */
public final class IrisProgramProvider implements GlProgramProvider {
    private final ClassLoader loader;
    private final GlApi gl;
    private DrawState drawState;
    private IrisReflectionBindings bindings;

    public IrisProgramProvider(ClassLoader loader, GlApi gl) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.gl = Objects.requireNonNull(gl, "gl");
    }

    @Override
    public boolean preview(FontRenderContext context) {
        return OptionalIrisProgramScope.preview(loader, context, this) != null;
    }

    @Override
    public GlVertexProducer createVertexProducer(GlApi gl, int capacity) {
        return new GlFontIrisProducer(gl, capacity);
    }

    /** 同一 ClassLoader 下复用 ABI 入口，每个 scope 仍读取当前 GL 上下文。
     * 绑定在有效 Iris pass 首次使用时完成，构造与缺席查询保留惰性加载边界。 */
    DrawState drawState() throws ReflectiveOperationException {
        if (drawState == null) drawState = DrawState.bind(loader);
        return drawState;
    }

    /** 复用成员元数据，pipeline、pass 与有效 GL 值由每次调用读取。 */
    IrisReflectionBindings bindings() throws ReflectiveOperationException {
        if (bindings == null) bindings = new IrisReflectionBindings(loader);
        return bindings;
    }

    @Override
    public GlProgramScope open(FontRenderContext context) {
        return OptionalIrisProgramScope.open(loader, gl, context, this);
    }
}
