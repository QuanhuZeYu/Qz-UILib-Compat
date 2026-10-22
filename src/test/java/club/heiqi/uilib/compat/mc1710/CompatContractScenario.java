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
        public static Object irisManager, irisPipeline;
        public static int pack, fullSources;
        public static boolean overrideShaders() { fullSources++; return false; }
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
            actualIrisInputs(angelica, expected);
            snapshotBoundary(angelica, expected);
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
                int reads = headers.fontResourceReads;
                club.heiqi.uilib.compat.mc1710.angelica.shared.IrisProgramProvider provider =
                        new club.heiqi.uilib.compat.mc1710.angelica.shared.IrisProgramProvider(headers, gl);
                require(headers.fontResourceReads == reads, "provider构造提前探测可选ABI");
                Method bind = provider.getClass().getDeclaredMethod("drawState");
                bind.setAccessible(true);
                Object bound = bind.invoke(provider);
                int boundReads = headers.fontResourceReads;
                require(boundReads > reads && bound.getClass().getSimpleName().equals(
                        expected.equals("v2210") ? "DrawState2210" : "DrawState2228"), "真实ABI绑定错误");
                for (int i = 0; i < 100; i++) require(bind.invoke(provider) == bound, "provider重复绑定ABI");
                require(headers.fontResourceReads == boundReads, "稳定Iris适配器重复解析class资源");
                Method queryBinding = provider.getClass().getDeclaredMethod("bindings");
                queryBinding.setAccessible(true);
                Object query = queryBinding.invoke(provider);
                Method worldBinding = query.getClass().getDeclaredMethod("world");
                worldBinding.setAccessible(true);
                Object world = worldBinding.invoke(query);
                require(worldBinding.invoke(query) == world, "稳定world成员重复装配");
                Field type = world.getClass().getDeclaredField("type"); type.setAccessible(true);
                require(((Class<?>) type.get(world)).getClassLoader() == headers, "绑定跨ClassLoader串用");
                System.out.println("IRIS_BINDING_REUSE_PASS " + expected + " repeated=100 resourceReads=0 lazy=true worldBinding=true ownLoader=true");
                System.out.println("ACTUAL_REFLECTION_BIND_PASS " + expected + " matrixProvider/lightmap/draw/uniform/VAO actualMemberHeaders=true gameStaticInit=false");
            }
            System.out.println("ACTUAL_ABI_PASS " + expected + " ctxPublic=" + publicContext + " AE2originalBody=1 new/end/call=1 targetLoads=0");
        }
    }
    private static Object uninitialized(Class<?> type) throws Exception {
        Class<?> unsafe = Class.forName("sun.misc.Unsafe");
        Field singleton = unsafe.getDeclaredField("theUnsafe"); singleton.setAccessible(true);
        return unsafe.getMethod("allocateInstance", Class.class).invoke(singleton.get(null), type);
    }
    private static void snapshotBoundary(String angelica, String expected) throws Exception {
        try (ActualJarHeaders headers = new ActualJarHeaders(new File(angelica), true)) {
            Projection.irisManager = uninitialized(headers.loadClass("net.coderbot.iris.pipeline.PipelineManager"));
            Class<?> deferred = headers.loadClass("net.coderbot.iris.pipeline.DeferredWorldRenderingPipeline");
            Object original = Projection.irisPipeline = uninitialized(deferred);
            Projection.pack = 7; Projection.fullSources = 0;
            Class<?> snapshot = Class.forName("club.heiqi.uilib.compat.mc1710.angelica.shared.HostProgramSnapshot");
            Class<?> source = Class.forName(snapshot.getName() + "$Capture");
            Constructor<?> create = source.getDeclaredConstructor(ClassLoader.class, club.heiqi.uilib.host.client.gl.GlApi.class);
            create.setAccessible(true);
            club.heiqi.uilib.host.client.gl.GlApi gl = (club.heiqi.uilib.host.client.gl.GlApi) java.lang.reflect.Proxy.newProxyInstance(
                    getLoader(), new Class<?>[] {club.heiqi.uilib.host.client.gl.GlApi.class},
                    (proxy, method, args) -> { throw new AssertionError("失效验证不能重新读取GL来源: " + method); });
            Object capture = create.newInstance(headers, gl);
            Method take = source.getDeclaredMethod("capture"); take.setAccessible(true);
            Method replay = snapshot.getDeclaredMethod("replay", snapshot, Runnable.class); replay.setAccessible(true);
            Method current = snapshot.getDeclaredMethod("current"); current.setAccessible(true);
            Object frozen = take.invoke(capture);
            require(frozen != null && Projection.fullSources == 1, "未读取实际签名的来源事实");
            int[] draws = {0};
            Runnable draw = () -> draws[0]++;
            for (int i = 0; i < 100; i++) replay.invoke(null, frozen, draw);
            require(draws[0] == 100 && Projection.fullSources == 1, "回放验证重新采集完整来源");
            Projection.irisPipeline = uninitialized(deferred);
            rejectSnapshot(replay, frozen, draw);
            Projection.irisPipeline = original; Projection.pack++;
            rejectSnapshot(replay, frozen, draw);
            require(draws[0] == 100, "失效来源仍绘制旧pass");
            Projection.pack--;
            RuntimeException sentinel = new RuntimeException("回放异常标记");
            Runnable nested = () -> {
                try {
                    require(current.invoke(null) == frozen, "外层来源未安装");
                    try {
                        replay.invoke(null, frozen, (Runnable) () -> { throw sentinel; });
                        throw new AssertionError("内层回放未执行异常路径");
                    }
                    catch (InvocationTargetException failure) { require(failure.getCause() == sentinel, "内层异常被替换"); }
                    require(current.invoke(null) == frozen, "嵌套异常未恢复外层来源");
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            };
            replay.invoke(null, frozen, nested);
            require(current.invoke(null) == null && Projection.fullSources == 1, "回放泄漏来源或重新采集");
            System.out.println("IRIS_SNAPSHOT_BOUNDARY_PASS " + expected + " replays=100 recapture=0 pipelineReloadRejected=true packReloadRejected=true nestedRestore=true");
        } finally {
            Projection.irisManager = Projection.irisPipeline = null;
        }
    }
    private static void rejectSnapshot(Method replay, Object snapshot, Runnable draw) throws Exception {
        try { replay.invoke(null, snapshot, draw); throw new AssertionError("未拒绝失效来源"); }
        catch (InvocationTargetException failure) {
            require(failure.getCause() instanceof IllegalStateException
                    && failure.getCause().getMessage().contains("pipeline/pack已失效"), "失效诊断变化: " + failure.getCause());
        }
    }

    /** 直接执行实际 Jar 的输入状态创建；header 替身无法验证枚举工厂与字段值。 */
    private static void actualIrisInputs(String angelica, String expected) throws Exception {
        try (URLClassLoader actual = new URLClassLoader(new URL[] {new File(angelica).toURI().toURL()}, getLoader())) {
            Class<?> inputs = actual.loadClass("net.coderbot.iris.gbuffer_overrides.matching.InputAvailability");
            Class<?> adapter = Class.forName("club.heiqi.uilib.compat.mc1710.angelica.shared.IrisInputAvailability");
            Constructor<?> binding = adapter.getDeclaredConstructor(Class.class);
            binding.setAccessible(true);
            Object factory = binding.newInstance(inputs);
            Method create = adapter.getDeclaredMethod("create", boolean.class, boolean.class);
            create.setAccessible(true);
            for (boolean texture : new boolean[] {false, true}) for (boolean lightmap : new boolean[] {false, true}) {
                Object value = create.invoke(factory, texture, lightmap);
                require(value.getClass() == inputs, "输入状态必须来自实际 Iris 类型");
                require(inputs.getField("texture").getBoolean(value) == texture, "纹理输入状态错误");
                require(inputs.getField("lightmap").getBoolean(value) == lightmap, "光照输入状态错误");
                require(((Number) inputs.getMethod("pack").invoke(value)).intValue() == ((texture ? 1 : 0) | (lightmap ? 2 : 0)), "输入状态编码错误");
            }
            System.out.println("ACTUAL_IRIS_INPUTS_PASS " + expected + " enum=" + inputs.isEnum() + " combinations=4");
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
        else throw new IllegalArgumentException("必须提供两组正式 Angelica/AE2 Jar，不能跳过真实 ABI 检查");
    }
}
