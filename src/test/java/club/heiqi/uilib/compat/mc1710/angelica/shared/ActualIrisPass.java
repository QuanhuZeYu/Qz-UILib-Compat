package club.heiqi.uilib.compat.mc1710.angelica.shared;

import static org.lwjgl.opengl.GL32C.*;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** 实际 Jar 的 Pass.use/useNow 执行体；仅替换不能在独立 GL 场景初始化的宿主类型。
 * FBO/program/viewport 调用进入真实 GL，目标选择分支完整保留。此夹具不初始化 Minecraft，
 * 不验证 pack 编译、pipeline 匹配、display-list 延期或非空 alpha/blend override。 */
public final class ActualIrisPass {
    private static final String PIPELINE = "net/coderbot/iris/pipeline/DeferredWorldRenderingPipeline";
    private static final String PASS = PIPELINE + "$Pass";
    private final Host host = new Host();
    private final MinecraftFixture minecraft;
    private final Object pass;
    private final Method use;
    private final Field shadowViewport;

    public ActualIrisPass(Path jar, int program, int beforeFramebuffer, int afterFramebuffer,
            int width, int height) throws Exception {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("测试尺寸必须为正");
        minecraft = new MinecraftFixture(width, height);
        host.shadowMapResolution = width;
        byte[] original; String entryName;
        try (JarFile source = new JarFile(jar.toFile())) {
            JarEntry entry = source.getJarEntry(PASS + ".class");
            Manifest manifest = source.getManifest();
            if (manifest != null && "true".equalsIgnoreCase(
                    manifest.getMainAttributes().getValue("Multi-Release"))) {
                for (int version = 21; version >= 9; version--) {
                    JarEntry candidate = source.getJarEntry("META-INF/versions/" + version + "/" + PASS + ".class");
                    if (candidate != null) { entry = candidate; break; }
                }
            }
            if (entry == null) throw new IllegalArgumentException("实际 Jar 缺少 " + PASS);
            entryName = entry.getName();
            try (InputStream input = source.getInputStream(entry)) { original = bytes(input); }
        }
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, ClassReader.SKIP_DEBUG);
        String generatedName = Type.getInternalName(ActualIrisPass.class) + "$Extracted";
        Map<String, String> types = new HashMap<>();
        types.put(PASS, generatedName); types.put(PIPELINE, Type.getInternalName(Host.class));
        types.put("net/coderbot/iris/gl/program/Program", Type.getInternalName(ProgramFixture.class));
        types.put("net/coderbot/iris/gl/framebuffer/GlFramebuffer", Type.getInternalName(FramebufferFixture.class));
        types.put("net/minecraft/client/Minecraft", Type.getInternalName(MinecraftFixture.class));
        types.put("net/minecraft/client/shader/Framebuffer", Type.getInternalName(Dimensions.class));
        types.put("net/coderbot/iris/uniforms/custom/CustomUniforms", Type.getInternalName(UniformsFixture.class));
        types.put("com/gtnewhorizons/angelica/glsm/ffp/Instancing", Type.getInternalName(InstancingFixture.class));
        for (String name : Arrays.asList("AlphaTestOverride", "BlendModeOverride", "BufferBlendOverride"))
            types.put("net/coderbot/iris/gl/blending/" + name, Type.getInternalName(UnsupportedOverride.class));
        types.put("net/coderbot/iris/gl/blending/DepthColorStorage", Type.getInternalName(DrawBoundary.class));
        types.put("com/gtnewhorizons/angelica/glsm/GLStateManager", Type.getInternalName(DrawBoundary.class));
        types.put("com/gtnewhorizons/angelica/iris/IrisDisplayListState", Type.getInternalName(DrawBoundary.class));
        Remapper remapper = new Remapper() {
            @Override public String map(String name) {
                String mapped = types.get(name);
                if (mapped != null) return mapped;
                if (name.startsWith("java/")) return name;
                throw new AssertionError("实际 Pass 出现未隔离宿主类型: " + name);
            }
        };
        ClassWriter output = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        output.visit(node.version, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, generatedName, null, "java/lang/Object", null);
        Set<String> fields = new HashSet<>(Arrays.asList("this$0", "program", "framebufferBeforeTranslucents",
                "framebufferAfterTranslucents", "alphaTestOverride", "blendModeOverride", "bufferBlendOverrides", "shadowViewport"));
        for (FieldNode field : node.fields) if (fields.remove(field.name))
            output.visitField(Opcodes.ACC_PUBLIC, field.name, remapper.mapDesc(field.desc), null, null).visitEnd();
        if (!fields.isEmpty()) throw new AssertionError("实际 Pass 字段缺失: " + fields);
        MethodVisitor init = output.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode(); init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN); init.visitMaxs(0, 0); init.visitEnd();
        List<String> methods = new ArrayList<>();
        for (MethodNode method : node.methods) if ((method.name.equals("use") || method.name.equals("useNow"))
                && method.desc.equals("()V")) {
            // MethodRemapper 仅映射类型/owner/descriptor；跳转、字段名、常量和调用顺序均来自原 Jar。
            method.accept(new MethodRemapper(output.visitMethod(method.access, method.name,
                    remapper.mapMethodDesc(method.desc), null, null), remapper));
            methods.add(method.name + method.desc);
        }
        if (!methods.contains("use()V")) throw new AssertionError("实际 Pass.use()V 缺失");
        output.visitEnd();
        Class<?> type = new ExtractedLoader().define(output.toByteArray());
        pass = type.getConstructor().newInstance();
        put(type, "this$0", host); put(type, "program", new ProgramFixture(program));
        put(type, "framebufferBeforeTranslucents", new FramebufferFixture(beforeFramebuffer));
        put(type, "framebufferAfterTranslucents", new FramebufferFixture(afterFramebuffer));
        put(type, "bufferBlendOverrides", Collections.emptyList());
        use = type.getMethod("use"); shadowViewport = type.getField("shadowViewport");
        String jarHash;
        try (InputStream input = Files.newInputStream(jar)) { jarHash = digest(input); }
        System.out.println("ACTUAL_IRIS_PASS_SOURCE jar=" + jar.toAbsolutePath() + " sha256=" + jarHash
                + " entry=" + entryName + " classSha256=" + digest(new ByteArrayInputStream(original))
                + " methods=" + methods + " typeRemapOnly=true realMinecraft=false");
    }

    public void use(boolean before, boolean shadow) {
        host.isBeforeTranslucent = before; MinecraftFixture.current = minecraft;
        try { shadowViewport.setBoolean(pass, shadow); use.invoke(pass); }
        catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Error) throw (Error) cause;
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new IllegalStateException("实际 Pass 执行失败", cause);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }

    private void put(Class<?> type, String field, Object value) throws Exception { type.getField(field).set(pass, value); }
    private static byte[] bytes(InputStream input) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int count;
        while ((count = input.read(buffer)) != -1) result.write(buffer, 0, count);
        return result.toByteArray();
    }
    private static String digest(InputStream input) throws Exception {
        MessageDigest hash = MessageDigest.getInstance("SHA-256"); byte[] buffer = new byte[8192]; int count;
        while ((count = input.read(buffer)) != -1) hash.update(buffer, 0, count);
        StringBuilder text = new StringBuilder();
        for (byte value : hash.digest()) text.append(String.format(Locale.ROOT, "%02x", value & 255));
        return text.toString();
    }
    private static final class ExtractedLoader extends ClassLoader {
        ExtractedLoader() { super(ActualIrisPass.class.getClassLoader()); }
        Class<?> define(byte[] code) { return defineClass(null, code, 0, code.length); }
    }

    public static final class Host {
        public boolean isBeforeTranslucent;
        public int shadowMapResolution;
        public InstancingFixture activeInstancing = InstancingFixture.NONE;
        public UniformsFixture customUniforms = new UniformsFixture();
        public void bindInstancedVariant(InstancingFixture mode) { throw new AssertionError("未覆盖实例化分支: " + mode); }
    }
    public enum InstancingFixture { NONE }
    public static final class FramebufferFixture {
        private final int id;
        FramebufferFixture(int id) { this.id = id; }
        public void bind() { glBindFramebuffer(GL_FRAMEBUFFER, id); }
    }
    public static final class ProgramFixture {
        private final int id;
        ProgramFixture(int id) { this.id = id; }
        public void use() { glUseProgram(id); }
    }
    public static final class Dimensions {
        public final int field_147621_c, field_147618_d;
        Dimensions(int width, int height) { field_147621_c = width; field_147618_d = height; }
    }
    public static final class MinecraftFixture {
        private static MinecraftFixture current;
        private final Dimensions framebuffer;
        MinecraftFixture(int width, int height) { framebuffer = new Dimensions(width, height); }
        public static MinecraftFixture func_71410_x() { return Objects.requireNonNull(current, "MC 尺寸边界未设置"); }
        public Dimensions func_147110_a() { return framebuffer; }
    }
    /** 本场景没有 override 或锁；restore/unlock 为明确的空边界，apply 不允许静默通过。 */
    public static final class UnsupportedOverride {
        public void apply() { throw new AssertionError("未覆盖非空 alpha/blend override"); }
        public static void restore() { }
    }
    public static final class DrawBoundary {
        public static void unlockDepthColor() { }
        public static void glViewport(int x, int y, int width, int height) { org.lwjgl.opengl.GL32C.glViewport(x, y, width, height); }
        // 独立 GL 测试没有 display list；只测试立即执行的 Pass 路由。
        public static void runProgramTransition(Runnable transition) { transition.run(); }
    }
    /** pack 的 custom uniform 求值不属于本场景；push 调用本身仍由真实方法体触发。 */
    public static final class UniformsFixture {
        public void push(Object owner) { Objects.requireNonNull(owner, "Pass uniform owner"); }
    }
}
