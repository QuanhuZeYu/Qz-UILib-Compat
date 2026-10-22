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

/** MC outer的可选Iris接入；GPU binding由outer注入，缺席查询不创建scope或初始化GL。 */
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

    /** ABI只随本provider的ClassLoader变化；每scope仍从绑定方法读取当前GL上下文。
     * 在有效Iris pass首次使用时绑定，构造/缺席路径不加载可选宿主。 */
    DrawState drawState() throws ReflectiveOperationException {
        if (drawState == null) drawState = DrawState.bind(loader);
        return drawState;
    }

    /** 只复用成员元数据；pipeline、pass与有效GL值仍由每次调用读取。 */
    IrisReflectionBindings bindings() throws ReflectiveOperationException {
        if (bindings == null) bindings = new IrisReflectionBindings(loader);
        return bindings;
    }

    @Override
    public GlProgramScope open(FontRenderContext context) {
        return OptionalIrisProgramScope.open(loader, gl, context, this);
    }
}
