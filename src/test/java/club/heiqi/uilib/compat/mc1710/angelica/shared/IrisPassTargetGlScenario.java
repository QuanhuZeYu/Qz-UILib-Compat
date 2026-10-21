package club.heiqi.uilib.compat.mc1710.angelica.shared;

import static org.lwjgl.opengl.GL32C.*;
import club.heiqi.uilib.host.client.gl.*;
import club.heiqi.uilib.host.client.font.context.*;
import club.heiqi.uilib.testing.GlfwTestEnvironment;
import club.heiqi.uilib.ui.render.batch.*;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.*;
import org.lwjgl.system.MemoryUtil;

/** 实际 Pass 方法体 + 生产共享 backend/producer 的目标像素验证；隔离 MC 初始化，不冒称完整游戏。 */
public final class IrisPassTargetGlScenario {
    private static final int W = 64, H = 48;
    public static void main(String[] jars) throws Exception {
        if (jars.length == 0) throw new IllegalArgumentException("需要实际 Angelica Jar");
        try (GlfwTestEnvironment host = new GlfwTestEnvironment();
                GlfwTestEnvironment.Window window = host.window(W, H, "Iris pass 目标", GlfwTestEnvironment.Profile.CORE, 3)) {
            int vertex = shader(GL_VERTEX_SHADER, "#version 150\nin vec4 position,color;out vec4 tint;void main(){gl_Position=position;tint=color;}");
            int fragment = shader(GL_FRAGMENT_SHADER, "#version 150\nin vec4 tint;out vec4 result;void main(){result=tint;}");
            int program = glCreateProgram();
            int[] fbos = new int[3], textures = new int[3];
            try {
                glAttachShader(program, vertex); glAttachShader(program, fragment);
                glBindAttribLocation(program, 0, "position"); glBindAttribLocation(program, 1, "color");
                glBindFragDataLocation(program, 0, "result"); glLinkProgram(program);
                require(glGetProgrami(program, GL_LINK_STATUS) != 0, glGetProgramInfoLog(program));
                for (int i = 0; i < fbos.length; i++) {
                    textures[i] = glGenTextures(); glBindTexture(GL_TEXTURE_2D, textures[i]);
                    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, W, H, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
                    fbos[i] = glGenFramebuffers(); glBindFramebuffer(GL_FRAMEBUFFER, fbos[i]);
                    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, textures[i], 0);
                    require(glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE, "测试目标不完整");
                }
                GlApi api = new Lwjgl3GlApi();
                float[] identity = {1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};
                FontRenderContext context = new FontRenderContext(
                        new FontProjection(identity, identity, new int[] {0,0,W,H}), fbos[0], H,
                        false, GL_ALWAYS, false, false, new int[] {0,0,W,H},
                        FontRenderContext.MatrixState.ui(identity, identity), api.capabilities().contextIdentity, 1, 0);
                List<GlProgramAttribute> attributes = Arrays.asList(
                        new GlProgramAttribute("position", 0, GL_FLOAT_VEC4, 1),
                        new GlProgramAttribute("color", 1, GL_FLOAT_VEC4, 1));
                for (String jar : jars) {
                    ActualIrisPass pass = new ActualIrisPass(Paths.get(jar), program, fbos[1], fbos[2], W, H);
                    for (boolean before : new boolean[] {true, false})
                        for (boolean shadow : new boolean[] {false, true}) {
                            verify(api, context, attributes, pass, program, textures[0], fbos, before, shadow, false);
                            verify(api, context, attributes, pass, program, textures[0], fbos, before, shadow, true);
                        }
                    System.out.println("ACTUAL_PASS_TARGET_PASS jar=" + jar
                            + " beforeAfter=true shadowViewport=true distinctCallerTarget=true pixels=true exceptionRestore=true realMinecraft=false");
                }
                require(glGetError() == GL_NO_ERROR, "pass 目标验证产生 GL error");
            } finally {
                glUseProgram(0); glBindFramebuffer(GL_FRAMEBUFFER, 0);
                for (int fbo : fbos) glDeleteFramebuffers(fbo);
                for (int texture : textures) glDeleteTextures(texture);
                glDeleteProgram(program); glDeleteShader(vertex); glDeleteShader(fragment);
            }
        }
    }

    private static void verify(GlApi api, FontRenderContext context, List<GlProgramAttribute> attributes,
            ActualIrisPass pass, int program, int texture, int[] fbos, boolean before, boolean shadow, boolean fail) {
        glDisable(GL_SCISSOR_TEST); glDisable(GL_BLEND); glDisable(GL_DITHER);
        glDisable(GL_FRAMEBUFFER_SRGB); glDisable(GL_CULL_FACE); glDisable(GL_STENCIL_TEST);
        glColorMask(true, true, true, true);
        for (int fbo : fbos) { glBindFramebuffer(GL_FRAMEBUFFER, fbo); glClearColor(0,0,0,0); glClear(GL_COLOR_BUFFER_BIT); }
        glBindFramebuffer(GL_FRAMEBUFFER, 0); glUseProgram(0);
        glViewport(3, 5, 19, 23); glDepthFunc(GL_GEQUAL); glDepthMask(true); glEnable(GL_DEPTH_TEST);
        GlProgramProvider factory = new IrisProgramProvider(IrisPassTargetGlScenario.class.getClassLoader(), api);
        GlProgramProvider provider = new GlProgramProvider() {
            public boolean preview(FontRenderContext value) { return true; }
            public GlVertexProducer createVertexProducer(GlApi value, int capacity) { return factory.createVertexProducer(value, capacity); }
            public GlProgramScope open(FontRenderContext value) {
                pass.use(before, shadow);
                require(glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING) == fbos[before ? 1 : 2], "实际 Pass 选择了错误阶段目标");
                int[] viewport = new int[4]; glGetIntegerv(GL_VIEWPORT, viewport);
                require(Arrays.equals(viewport, new int[] {0,0,W,shadow ? W : H}), "实际 Pass viewport 分支未命中");
                return new GlProgramScope() {
                    public int programId() { return program; }
                    public List<GlProgramAttribute> attributes() { return attributes; }
                    public float[] normal() { return new float[] {0,0,1}; }
                    public int packedLight() { return 0; }
                    public float[] extended(String name, int type) { throw new AssertionError("本案例无扩展字段"); }
                    public void configure(int vao, int vbo, int ebo, boolean uv, boolean color, boolean lightmap, boolean normal) {
                        glBindVertexArray(vao); glBindBuffer(GL_ARRAY_BUFFER, vbo); glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
                    }
                    public void pointer(int location, int size, int type, boolean normalized, int stride, long offset) {
                        glVertexAttribPointer(location, size, type, normalized, stride, offset); glEnableVertexAttribArray(location);
                    }
                    public void disable(int location) { glDisableVertexAttribArray(location); }
                    public void bindTexture2D(int id) { glBindTexture(GL_TEXTURE_2D, id); }
                    public void beforeDraw() {
                        pass.use(before, shadow);
                        if (fail) throw new IllegalStateException("目标恢复反例");
                    }
                    public void close() {
                        // 先核对共享 world/scope 已恢复，不能让测试宿主的清理掩盖 backend 的恢复错误。
                        int[] viewport = new int[4]; glGetIntegerv(GL_VIEWPORT, viewport);
                        require(glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING) == 0
                                && glGetInteger(GL_CURRENT_PROGRAM) == 0
                                && Arrays.equals(viewport, new int[] {3,5,19,23})
                                && glGetInteger(GL_DEPTH_FUNC) == GL_GEQUAL
                                && glGetBoolean(GL_DEPTH_WRITEMASK) && glIsEnabled(GL_DEPTH_TEST),
                                "宿主 close 前共享 backend 尚未恢复入口状态");
                        // 宿主 scope 为测试边界；真实 OptionalIrisProgramScope 的缓存恢复仍由游戏验收。
                        glBindFramebuffer(GL_FRAMEBUFFER, 0); glUseProgram(0);
                    }
                };
            }
        };
        try (GlQuadRenderer renderer = new GlQuadRenderer(api, 4,
                (resource, generation) -> new GlQuadRenderer.Texture(GL_TEXTURE_2D, texture), provider)) {
            try (GlQuadRenderer.Scope scope = renderer.begin(context)) {
                require(glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING) == fbos[before ? 1 : 2],
                        "生产 backend.begin 覆盖了实际 Pass 目标");
                OrderedQuadCollector collector = new OrderedQuadCollector(4, scope::consumeBatch);
                collector.add(new OrderedQuadBatch.State(GlQuadRenderer.MONOCHROME_COVERAGE, texture, 1, null),
                        new OrderedQuadBatch.Quad(-0.5f,-0.5f,1,1,0,0,1,1,0,0x80ff8040));
                collector.flush();
                require(!fail, "异常反例未触发");
            } catch (IllegalStateException expected) {
                if (!fail || !"目标恢复反例".equals(expected.getMessage())) throw expected;
            }
        }
        int[] restored = new int[4]; glGetIntegerv(GL_VIEWPORT, restored);
        require(glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING) == 0 && glGetInteger(GL_READ_FRAMEBUFFER_BINDING) == 0
                && glGetInteger(GL_CURRENT_PROGRAM) == 0 && Arrays.equals(restored, new int[] {3,5,19,23})
                && glGetInteger(GL_DEPTH_FUNC) == GL_GEQUAL && glGetBoolean(GL_DEPTH_WRITEMASK) && glIsEnabled(GL_DEPTH_TEST),
                "backend scope 未恢复入口目标/program/viewport/depth");
        for (int i = 0; i < fbos.length; i++) {
            glBindFramebuffer(GL_READ_FRAMEBUFFER, fbos[i]);
            ByteBuffer pixel = MemoryUtil.memAlloc(4);
            try {
                glReadPixels(W/2, H/2, 1,1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
                int[] expected = !fail && i == (before ? 1 : 2) ? new int[] {255,128,64,128} : new int[4];
                for (int c = 0; c < 4; c++) require(Math.abs((pixel.get(c)&255)-expected[c]) <= 1,
                        "pass 目标像素或历史 caller 隔离不符 target=" + i + " channel=" + c);
            } finally { MemoryUtil.memFree(pixel); }
        }
        glBindFramebuffer(GL_READ_FRAMEBUFFER, 0);
    }
    private static int shader(int type, String code) {
        int shader = glCreateShader(type); glShaderSource(shader, code); glCompileShader(shader);
        require(glGetShaderi(shader, GL_COMPILE_STATUS) != 0, glGetShaderInfoLog(shader)); return shader;
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
