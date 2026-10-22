package club.heiqi.uilib.compat.mc1710;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** 从实际Jar产生只含原始成员签名的可加载header，避免静态初始化游戏/GL。
 * 保留真实字段、descriptor、访问位及继承；仅剥离执行体，缺少的MC参数类型用空壳占位。
 * 生产反射构造器直接绑定这些原始签名，能发现真实ABI迁移，不能代替游戏执行验证。 */
final class ActualJarHeaders extends ClassLoader implements AutoCloseable {
    private final Map<String, byte[]> classes = new HashMap<>();
    int fontResourceReads;
    boolean matrixQueries, boxedIntegerResult;
    private final boolean snapshotFacts;
    ActualJarHeaders(File file) throws IOException { this(file, false); }
    ActualJarHeaders(File file, boolean snapshotFacts) throws IOException {
        super(ActualJarHeaders.class.getClassLoader());
        this.snapshotFacts = snapshotFacts;
        try (JarFile jar = new JarFile(file)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                try (InputStream input = jar.getInputStream(entry); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192]; int size;
                    while ((size = input.read(buffer)) >= 0) bytes.write(buffer, 0, size);
                    classes.put(entry.getName().substring(0, entry.getName().length() - 6).replace('/', '.'), bytes.toByteArray());
                }
            }
        }
    }
    @Override public InputStream getResourceAsStream(String name) {
        if (name.equals("com/gtnewhorizons/angelica/client/font/BatchingFontRenderer.class"))
            fontResourceReads++;
        byte[] code = classes.get(name.replace('/', '.').replaceFirst("[.]class$", ""));
        return code == null ? super.getResourceAsStream(name) : new ByteArrayInputStream(code);
    }
    @Override public URL getResource(String name) {
        if (classes.containsKey(name.replace('/', '.').replaceFirst("[.]class$", ""))) {
            try { return new File(".").toURI().toURL(); } catch (MalformedURLException error) { throw new AssertionError(error); }
        }
        return super.getResource(name);
    }
    @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> found = findLoadedClass(name);
            if (found != null) return found;
            byte[] original = classes.get(name);
            if (original == null) {
                try { return super.loadClass(name, resolve); }
                catch (ClassNotFoundException absent) {
                    if (name.startsWith("java.")) throw absent;
                    ClassWriter stub = new ClassWriter(0);
                    stub.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name.replace('.', '/'), null, "java/lang/Object", null); stub.visitEnd();
                    byte[] code = stub.toByteArray(); return defineClass(name, code, 0, code.length);
                }
            }
            ClassNode node = new ClassNode(); new ClassReader(original).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            String parent = classes.containsKey(node.superName.replace('/', '.')) ? node.superName : "java/lang/Object";
            String[] interfaces = node.interfaces.stream().filter(value -> classes.containsKey(value.replace('/', '.'))).toArray(String[]::new);
            ClassWriter output = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            output.visit(Opcodes.V1_8, node.access, node.name, null, parent, interfaces);
            for (FieldNode field : node.fields) output.visitField(field.access, field.name, field.desc, null, field.value).visitEnd();
            for (MethodNode method : node.methods) {
                if (method.name.equals("<clinit>")) continue;
                int access = method.access & ~Opcodes.ACC_NATIVE;
                if (boxedIntegerResult && node.name.equals("com/gtnewhorizons/angelica/glsm/GLStateManager")
                        && method.name.equals("glGetInteger") && method.desc.equals("(I)I"))
                    method.desc = "(I)Ljava/lang/Integer;";
                MethodVisitor body = output.visitMethod(access, method.name, method.desc, null, null);
                if ((access & Opcodes.ACC_ABSTRACT) == 0) {
                    body.visitCode();
                    if (method.name.equals("<init>")) { body.visitVarInsn(Opcodes.ALOAD, 0); body.visitMethodInsn(Opcodes.INVOKESPECIAL, parent, "<init>", "()V", false); }
                    Type result = Type.getReturnType(method.desc);
                    if ((snapshotFacts && snapshotBody(body, node.name, method, result))
                            || (matrixQueries && matrixBody(body, node.name, method, result))) {
                        body.visitMaxs(0, 0); body.visitEnd(); continue;
                    }
                    switch (result.getSort()) {
                        case Type.VOID: body.visitInsn(Opcodes.RETURN); break;
                        case Type.LONG: body.visitInsn(Opcodes.LCONST_0); body.visitInsn(Opcodes.LRETURN); break;
                        case Type.FLOAT: body.visitInsn(Opcodes.FCONST_0); body.visitInsn(Opcodes.FRETURN); break;
                        case Type.DOUBLE: body.visitInsn(Opcodes.DCONST_0); body.visitInsn(Opcodes.DRETURN); break;
                        case Type.ARRAY: case Type.OBJECT: body.visitInsn(Opcodes.ACONST_NULL); body.visitInsn(Opcodes.ARETURN); break;
                        default: body.visitInsn(Opcodes.ICONST_0); body.visitInsn(Opcodes.IRETURN);
                    }
                    body.visitMaxs(0, 0);
                }
                body.visitEnd();
            }
            output.visitEnd(); byte[] code = output.toByteArray(); return defineClass(name, code, 0, code.length);
        }
    }
    /** 只替换实际Iris的动态边界事实，成员签名仍来自发行Jar；不执行游戏静态初始化。 */
    private boolean snapshotBody(MethodVisitor body, String owner, MethodNode method, Type result) {
        String projection = "club/heiqi/uilib/compat/mc1710/CompatContractScenario$Projection";
        String field = null;
        if (owner.equals("net/coderbot/iris/Iris")) {
            if (method.name.equals("getShaderPackLoadId")) {
                body.visitFieldInsn(Opcodes.GETSTATIC, projection, "pack", "I");
                body.visitInsn(Opcodes.IRETURN); return true;
            }
            if (method.name.equals("getPipelineManagerNullable")) field = "irisManager";
        }
        if (owner.equals("net/coderbot/iris/pipeline/PipelineManager")
                && method.name.equals("getPipelineNullable")) field = "irisPipeline";
        if (field != null) {
            body.visitFieldInsn(Opcodes.GETSTATIC, projection, field, "Ljava/lang/Object;");
            body.visitTypeInsn(Opcodes.CHECKCAST, result.getInternalName());
            body.visitInsn(Opcodes.ARETURN); return true;
        }
        if (owner.equals("net/coderbot/iris/pipeline/DeferredWorldRenderingPipeline")
                && method.name.equals("shouldOverrideShaders")) {
            body.visitMethodInsn(Opcodes.INVOKESTATIC, projection, "overrideShaders", "()Z", false);
            body.visitInsn(Opcodes.IRETURN); return true;
        }
        return false;
    }
    /** 只给查询入口供值，执行生产provider与真实descriptor；不模拟GLSM/Iris有效状态算法。 */
    private boolean matrixBody(MethodVisitor body, String owner, MethodNode method, Type result) {
        String projection = "club/heiqi/uilib/compat/mc1710/CompatContractScenario$Projection";
        if (owner.equals("com/gtnewhorizons/angelica/glsm/GLStateManager")) {
            String query = null;
            if (method.name.equals("glGetInteger") && method.desc.equals("(I)I")) query = "matrixInteger";
            if (method.name.equals("glIsEnabled") && method.desc.equals("(I)Z")) query = "matrixEnabled";
            if (method.name.equals("glGetInteger") && method.desc.equals("(ILjava/nio/IntBuffer;)V")) query = "matrixIntegers";
            if (method.name.equals("glGetBoolean") && method.desc.equals("(ILjava/nio/ByteBuffer;)V")) query = "matrixBooleans";
            if (method.name.equals("glGetFloat") && method.desc.equals("(ILjava/nio/FloatBuffer;)V")) query = "matrixFloats";
            if (query != null) {
                body.visitVarInsn(Opcodes.ILOAD, 0);
                if (result.getSort() == Type.VOID) body.visitVarInsn(Opcodes.ALOAD, 1);
                body.visitMethodInsn(Opcodes.INVOKESTATIC, projection, query, method.desc, false);
                body.visitInsn(result.getSort() == Type.VOID ? Opcodes.RETURN : Opcodes.IRETURN); return true;
            }
            String field = null;
            if (method.name.equals("isCachingEnabled")) field = "matrixCaching";
            if (method.name.equals("isEffectiveDepthMaskEnabled")) field = "matrixEnabledValue";
            if (field != null) {
                body.visitFieldInsn(Opcodes.GETSTATIC, projection, field, "Z"); body.visitInsn(Opcodes.IRETURN); return true;
            }
            if (method.name.equals("getStencilState")) field = "matrixStencil";
            if (method.name.equals("getPolygonState")) field = "matrixPolygon";
            if (field != null) {
                body.visitFieldInsn(Opcodes.GETSTATIC, projection, field, "Ljava/lang/Object;");
                body.visitTypeInsn(Opcodes.CHECKCAST, result.getInternalName()); body.visitInsn(Opcodes.ARETURN); return true;
            }
        }
        if (owner.equals("com/gtnewhorizons/angelica/glsm/states/StencilState") && method.name.equals("getFuncBack")) {
            body.visitFieldInsn(Opcodes.GETSTATIC, projection, "matrixIntegerValue", "I"); body.visitInsn(Opcodes.IRETURN); return true;
        }
        if (owner.equals("com/gtnewhorizons/angelica/glsm/states/PolygonState")
                && (method.name.equals("getOffsetFactor") || method.name.equals("getOffsetUnits"))) {
            body.visitFieldInsn(Opcodes.GETSTATIC, projection, "matrixFloatValue", "F"); body.visitInsn(Opcodes.FRETURN); return true;
        }
        return false;
    }
    public void close() { classes.clear(); }
}
