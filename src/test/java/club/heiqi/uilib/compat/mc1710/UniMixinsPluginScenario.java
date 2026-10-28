package club.heiqi.uilib.compat.mc1710;

import java.io.*;
import java.net.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.*;

/** 复现 #69 实机缺陷：验证 UniMixins 映射 ASM 包后的插件加载与方法签名唯一性。 */
public final class UniMixinsPluginScenario {
    private static final String PLUGIN = "club.heiqi.uilib.compat.mixin.CompatMixinPlugin";
    private static byte[] bytes(InputStream input) throws IOException {
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int size; while ((size = stream.read(buffer)) >= 0) output.write(buffer, 0, size); return output.toByteArray();
        }
    }
    private static void unique(byte[] code) {
        ClassNode node = new ClassNode(); new ClassReader(code).accept(node, ClassReader.SKIP_CODE);
        Set<String> signatures = new HashSet<>();
        for (MethodNode method : node.methods) if (!signatures.add(method.name + method.desc)) throw new AssertionError("重映射后重复方法: " + method.name + method.desc);
    }
    private static byte[] mapped(byte[] code, String prefix) {
        ClassWriter output = new ClassWriter(0);
        new ClassReader(code).accept(new ClassRemapper(output, new Remapper() {
            @Override public String map(String name) {
                return name.startsWith("org/objectweb/asm/") ? prefix + name.substring("org/objectweb/asm/".length()) : name;
            }
        }), 0); return output.toByteArray();
    }
    private static final class RuntimeLoader extends URLClassLoader {
        RuntimeLoader(URL[] urls) { super(urls, UniMixinsPluginScenario.class.getClassLoader()); }
        Class<?> plugin(byte[] code) { return defineClass(PLUGIN, code, 0, code.length); }
        @Override public URL getResource(String name) { URL found = findResource(name); return found == null ? super.getResource(name) : found; }
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("com.gtnewhorizons.angelica.")) throw new ClassNotFoundException("插件不得提前加载目标: " + name);
            if (name.startsWith("org.spongepowered.")) {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> found = findLoadedClass(name); if (found == null) found = findClass(name);
                    if (resolve) resolveClass(found); return found;
                }
            }
            return super.loadClass(name, resolve);
        }
    }
    private static void actual(byte[] source, String uniMixins, String angelica, String abi) throws Exception {
        try (RuntimeLoader loader = new RuntimeLoader(new URL[] {new File(uniMixins).toURI().toURL(), new File(angelica).toURI().toURL()})) {
            ClassNode contract = new ClassNode();
            new ClassReader(bytes(loader.getResourceAsStream("org/spongepowered/asm/mixin/extensibility/IMixinConfigPlugin.class"))).accept(contract, ClassReader.SKIP_CODE);
            String prefix = null;
            for (MethodNode method : contract.methods) if (method.name.equals("preApply")) {
                String classNode = org.objectweb.asm.Type.getArgumentTypes(method.desc)[1].getInternalName();
                prefix = classNode.substring(0, classNode.indexOf("tree/ClassNode"));
            }
            if (prefix == null) throw new AssertionError("实际UniMixins缺少插件合同");
            Class<?> transformer = loader.loadClass("io.github.legacymoddingmc.unimixins.compat.asm.ASMRemapperTransformer");
            Field realPrefix = transformer.getDeclaredField("realASMPackagePrefix"); realPrefix.setAccessible(true);
            // 直接提供与生产一致的探测结果，隔离 Launch 启动；后续 transform 完整执行实际 Jar 实现。
            realPrefix.set(null, prefix);
            Object remapper = transformer.getConstructor().newInstance();
            byte[] transformed = (byte[]) transformer.getMethod("transform", String.class, String.class, byte[].class).invoke(remapper, PLUGIN, PLUGIN, source);
            unique(transformed);
            Class<?> plugin = loader.plugin(transformed);
            Class<?> pluginInterface = loader.loadClass("org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin");
            if (!pluginInterface.isAssignableFrom(plugin)) throw new AssertionError("插件运行ABI不匹配");
            Object instance = plugin.getConstructor().newInstance();
            for (Method method : pluginInterface.getMethods()) plugin.getMethod(method.getName(), method.getParameterTypes());
            Method select = plugin.getMethod("shouldApplyMixin", String.class, String.class);
            String target = "com.gtnewhorizons.angelica.client.font.BatchingFontRenderer";
            for (String candidate : new String[] {"v2210", "v2228"}) {
                boolean enabled = (Boolean) select.invoke(instance, target, "club.heiqi.uilib.compat.mixin.mc1710.angelica." + candidate + ".AngelicaFontOrderMixin");
                if (enabled != candidate.equals(abi)) throw new AssertionError("插件选错目标ABI " + abi + " -> " + candidate);
            }
            if ((Boolean) select.invoke(instance, "absent.Target", "mc1710.angelica.v2210.Any")) throw new AssertionError("缺席目标启用");
            System.out.println("ACTUAL_UNIMIXINS_PLUGIN_PASS " + abi + " prefix=" + prefix + " transformedUnique=true loaded=true interfaceMethodsBound=true singleAbiSelected=true");
        }
    }
    public static void main(String[] args) throws Exception {
        byte[] source = bytes(UniMixinsPluginScenario.class.getClassLoader().getResourceAsStream(PLUGIN.replace('.', '/') + ".class"));
        unique(mapped(source, "org/spongepowered/asm/lib/"));
        // 重建实机已确认的双 ASM 重载反例，验证重映射后可检出签名冲突。
        ClassNode broken = new ClassNode(); new ClassReader(source).accept(broken, 0);
        for (MethodNode method : new ArrayList<>(broken.methods)) if (method.name.equals("preApply")) {
            MethodNode duplicate = new MethodNode(Opcodes.ACC_PUBLIC, method.name, method.desc.replace("org/objectweb/asm/", "org/spongepowered/asm/lib/"), null, null);
            duplicate.instructions.add(new InsnNode(Opcodes.RETURN)); duplicate.maxLocals = method.maxLocals; broken.methods.add(duplicate);
        }
        ClassWriter writer = new ClassWriter(0); broken.accept(writer); boolean rejected = false;
        try { unique(mapped(writer.toByteArray(), "org/spongepowered/asm/lib/")); } catch (AssertionError expected) { rejected = true; }
        if (!rejected) throw new AssertionError("未捕获实际双重声明回归");
        System.out.println("UNIMIXINS_DUPLICATE_GUARD_PASS currentUnique=true oldDualOverloadRejected=true");
        if (args.length != 3) throw new IllegalArgumentException("必须提供正式 UniMixins 和两个 Angelica Jar");
        actual(source, args[0], args[1], "v2210"); actual(source, args[0], args[2], "v2228");
    }
}
