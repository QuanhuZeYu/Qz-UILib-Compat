package club.heiqi.uilib.compat.mc1710;

import java.io.*;
import java.net.*;
import java.lang.reflect.*;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import club.heiqi.uilib.compat.mixin.CompatMixinPlugin;
import club.heiqi.uilib.host.client.extension.MinecraftHostExtension;

/** 实际Jar只读ABI与缺席加载；AE2反例执行生产Mixin方法体，仅替换外部配置投影。 */
public final class CompatContractScenario {
    public static final class Projection {
        public static boolean enabled;
        public static long revision;
        public static boolean enabled() { return enabled; }
        public static long fontGeneration() { return revision; }
    }
    private static ClassLoader getLoader() { return CompatContractScenario.class.getClassLoader(); }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static byte[] bytes(InputStream input) throws IOException {
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int size;
            while ((size = stream.read(buffer)) >= 0) output.write(buffer, 0, size);
            return output.toByteArray();
        }
    }
    private static ClassNode node(ClassLoader loader, String type) throws IOException {
        InputStream input = loader.getResourceAsStream(type.replace('.', '/') + ".class");
        require(input != null, "缺少实际class: " + type);
        ClassNode node = new ClassNode(); new ClassReader(bytes(input)).accept(node, 0); return node;
    }
    private static MethodNode method(ClassNode node, String name, String descriptor) {
        for (MethodNode method : node.methods) if (method.name.equals(name) && method.desc.equals(descriptor)) return method;
        throw new AssertionError(node.name + "缺少方法 " + name + descriptor);
    }
    private static final class Resources extends URLClassLoader {
        int forbiddenLoads;
        Resources(URL[] jars) { super(jars, CompatContractScenario.class.getClassLoader()); }
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("com.gtnewhorizons.angelica.") || name.startsWith("net.coderbot.") || name.startsWith("appeng.") || name.startsWith("me.eigenraven.")) {
                forbiddenLoads++; throw new ClassNotFoundException("ABI检查禁止提前加载 " + name);
            }
            return super.loadClass(name, resolve);
        }
    }
    private static void actual(String angelica, String ae2, String expected, boolean publicContext) throws Exception {
        try (Resources loader = new Resources(new URL[] {new File(angelica).toURI().toURL(), new File(ae2).toURI().toURL()})) {
            require(expected.equals(AngelicaAbi.select(loader)), "选错实际Angelica ABI");
            ClassNode glsm = node(loader, "com.gtnewhorizons.angelica.glsm.GLStateManager");
            MethodNode ctx = method(glsm, "ctx", "()Lcom/gtnewhorizons/angelica/glsm/GLContextState;");
            require(((ctx.access & Opcodes.ACC_PUBLIC) != 0) == publicContext, "ctx可见性证据变化");
            ClassNode lists = node(loader, "com.gtnewhorizons.angelica.glsm.DisplayListManager");
            method(lists, "pauseRecording", "()Lcom/gtnewhorizons/angelica/glsm/recording/CommandRecorder;");
            method(lists, "resumeRecording", "(Lcom/gtnewhorizons/angelica/glsm/recording/CommandRecorder;)V");
            ClassNode monitor = node(loader, "appeng.parts.reporting.AbstractPartMonitor");
            MethodNode render = method(monitor, "renderDynamic", "(DDDLappeng/api/parts/IPartRenderHelper;Lnet/minecraft/client/renderer/RenderBlocks;)V");
            int begin = 0, end = 0, body = 0, call = 0;
            for (AbstractInsnNode instruction : render.instructions) if (instruction instanceof MethodInsnNode) {
                MethodInsnNode invocation = (MethodInsnNode) instruction;
                if (invocation.owner.equals("org/lwjgl/opengl/GL11")) {
                    if (invocation.name.equals("glNewList") && invocation.desc.equals("(II)V")) begin++;
                    if (invocation.name.equals("glEndList") && invocation.desc.equals("()V")) end++;
                    if (invocation.name.equals("glCallList") && invocation.desc.equals("(I)V")) call++;
                }
                if (invocation.name.equals("tesrRenderScreen")) body++;
            }
            require(begin == 1 && end == 1 && body == 1 && call == 1, "AE2显示列表接点数量变化");
            require(loader.forbiddenLoads == 0, "ABI选择提前加载了可选目标");
            try (ActualJarHeaders headers = new ActualJarHeaders(new File(angelica))) {
                club.heiqi.uilib.compat.mc1710.angelica.shared.LightmapState.bind(headers);
                club.heiqi.uilib.compat.mc1710.angelica.shared.DrawState.bind(headers);
                club.heiqi.uilib.host.client.gl.GlApi gl = (club.heiqi.uilib.host.client.gl.GlApi) java.lang.reflect.Proxy.newProxyInstance(
                        getLoader(), new Class<?>[] {club.heiqi.uilib.host.client.gl.GlApi.class},
                        (proxy, method, values) -> { throw new AssertionError("构造阶段不应访问GL: " + method); });
                club.heiqi.uilib.compat.mc1710.angelica.shared.AngelicaMatrixStateProvider.optional(headers, gl);
                System.out.println("ACTUAL_REFLECTION_BIND_PASS " + expected + " matrixProvider/lightmap/draw/uniform/VAO actualMemberHeaders=true gameStaticInit=false");
            }
            System.out.println("ACTUAL_ABI_PASS " + expected + " ctxPublic=" + publicContext + " AE2originalBody=1 new/end/call=1 targetLoads=0");
        }
    }
    private static Field field(Class<?> type, String name) throws Exception { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
    private static Method declared(Class<?> type, String name, Class<?>... parameters) throws Exception { Method method = type.getDeclaredMethod(name, parameters); method.setAccessible(true); return method; }
    private static void monitor(boolean logical) throws Exception {
        final String type = "club.heiqi.uilib.compat.mixin.mc1710.ae2.rv3.MonitorFontCacheMixin";
        ClassNode source = node(CompatContractScenario.class.getClassLoader(), type);
        source.access &= ~Opcodes.ACC_ABSTRACT;
        for (MethodNode method : source.methods) for (AbstractInsnNode instruction : method.instructions) if (instruction instanceof MethodInsnNode) {
            MethodInsnNode invocation = (MethodInsnNode) instruction;
            if (invocation.owner.equals("club/heiqi/uilib/host/client/font/VanillaFontOptions") || invocation.owner.equals("club/heiqi/uilib/host/client/font/MinecraftFontServices"))
                invocation.owner = Projection.class.getName().replace('.', '/');
        }
        ClassWriter writer = new ClassWriter(0); source.accept(writer); final byte[] code = writer.toByteArray();
        ClassLoader loader = new ClassLoader(CompatContractScenario.class.getClassLoader()) {
            @Override public URL getResource(String name) {
                if (name.equals("com/gtnewhorizons/angelica/glsm/DisplayListManager.class")) {
                    if (!logical) return null;
                    try { return new File(".").toURI().toURL(); } catch (MalformedURLException error) { throw new AssertionError(error); }
                }
                return super.getResource(name);
            }
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals(type)) { Class<?> found = findLoadedClass(name); return found == null ? defineClass(name, code, 0, code.length) : found; }
                return super.loadClass(name, resolve);
            }
        };
        Class<?> mixin = loader.loadClass(type); Object instance = mixin.getConstructor().newInstance();
        Class<?> callback = Class.forName("org.spongepowered.asm.mixin.injection.callback.CallbackInfo");
        Method invalidate = declared(mixin, "qz$invalidateFontCache", callback);
        Field dirty = field(mixin, "updateList");
        Projection.enabled = false; Projection.revision = 7;
        invalidate.invoke(instance, new Object[] {null}); require(dirty.getBoolean(instance), "首次录制");
        dirty.setBoolean(instance, false); invalidate.invoke(instance, new Object[] {null}); require(!dirty.getBoolean(instance), "未变不应重建");
        Projection.enabled = true; invalidate.invoke(instance, new Object[] {null}); require(dirty.getBoolean(instance), "开启未使原版缓存失效");
        dirty.setBoolean(instance, false); invalidate.invoke(instance, new Object[] {null}); require(dirty.getBoolean(instance) != logical, "有/无逻辑宿主策略错误");
        int[] operations = {0}; Operation<Void> operation = arguments -> { operations[0]++; return null; };
        declared(mixin, "qz$beginList", int.class, int.class, Operation.class).invoke(instance, 3, 0x1301, operation);
        declared(mixin, "qz$endList", Operation.class).invoke(instance, operation);
        require(operations[0] == (logical ? 2 : 0), "直接路径仍录制原生GL列表");
        dirty.setBoolean(instance, false); Projection.revision++; invalidate.invoke(instance, new Object[] {null}); require(dirty.getBoolean(instance), "资源换代未使缓存失效");
        dirty.setBoolean(instance, false); Projection.enabled = false; invalidate.invoke(instance, new Object[] {null}); require(dirty.getBoolean(instance), "关闭未重建原版列表");
        dirty.setBoolean(instance, false); invalidate.invoke(instance, new Object[] {null}); require(!dirty.getBoolean(instance), "关闭后重复重建");
        operations[0] = 0; declared(mixin, "qz$beginList", int.class, int.class, Operation.class).invoke(instance, 3, 0x1301, operation);
        declared(mixin, "qz$endList", Operation.class).invoke(instance, operation); require(operations[0] == 2, "关闭后未恢复原版列表");
        System.out.println("MONITOR_CONTRACT_PASS logical=" + logical + " off/on/off resourceGeneration originalBodyPreserved=true");
    }
    private static void absent() throws Exception {
        CompatMixinPlugin plugin = new CompatMixinPlugin();
        require(!plugin.shouldApplyMixin("missing.OptionalTarget", "mc1710.angelica.v2210.Any"), "缺席目标仍应用Mixin");
        try (Resources resources = new Resources(new URL[0])) { require(AngelicaAbi.select(resources) == null, "缺席ABI非null"); require(resources.forbiddenLoads == 0, "缺席加载目标"); }
        LegacyHostExtension service = new LegacyHostExtension();
        ClassLoader loader = CompatContractScenario.class.getClassLoader();
        require(service.fontBridge(loader, null, null, 2) == null, "缺席字体桥");
        require(service.matrices(loader, null) == null && service.programs(loader, null) == null && service.itemPort(loader) == null, "缺席图形能力");
        require(!service.nativeInputSupported(loader) && !service.nativeInputAvailable(loader) && service.nativeInput(loader) == null, "缺席原生输入");
        System.out.println("ABSENT_PASS providerConstructed=true optionalTargetLoads=0 mixinSelected=false");
    }
    public static void main(String[] args) throws Exception {
        absent(); monitor(false); monitor(true);
        if (args.length == 4) { actual(args[0], args[1], "v2210", false); actual(args[2], args[3], "v2228", true); }
        else System.out.println("实际Jar ABI未配置：使用-PcompatPrismRoot选择两个实例");
    }
}
