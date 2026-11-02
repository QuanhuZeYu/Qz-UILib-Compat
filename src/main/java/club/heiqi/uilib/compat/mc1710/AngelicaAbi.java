package club.heiqi.uilib.compat.mc1710;

import club.heiqi.uilib.compat.mc1710.lwjgl3ify.sdl.SceneNativeInputSupport;

/** 从 class 资源的 helper descriptor 选择 ABI，目标类由 Mixin 阶段加载；结构失配时报告错误。 */
public final class AngelicaAbi {
    private AngelicaAbi() {}

    /** 2.2.43 的 MR 宿主按职责拆出静态入口；只缓存 owner，当前 ctx 仍由官方入口按次取得。 */
    public static StateProfile states(Class<?> glsm) throws ReflectiveOperationException {
        return new StateProfile(glsm);
    }

    public static final class StateProfile {
        public final Class<?> glStateManager, queries, enable, matrices, blend, depthStencilAlpha, rasterizer, textures;
        private final Class<?> vertexInput, buffers, currentAttribs, attribStack, programs, framebuffer, texEnvGen, draws;

        private StateProfile(Class<?> glsm) throws ReflectiveOperationException {
            glStateManager = glsm;
            ClassLoader loader = glsm.getClassLoader();
            String prefix = "com.gtnewhorizons.angelica.glsm.";
            boolean split = loader.getResource(prefix.replace('.', '/') + "GLSMQueries.class") != null;
            queries = split ? Class.forName(prefix + "GLSMQueries", false, loader) : glsm;
            enable = split ? Class.forName(prefix + "GLSMEnable", false, loader) : glsm;
            matrices = split ? Class.forName(prefix + "GLSMMatrices", false, loader) : glsm;
            blend = split ? Class.forName(prefix + "GLSMBlend", false, loader) : glsm;
            depthStencilAlpha = split ? Class.forName(prefix + "GLSMDepthStencilAlpha", false, loader) : glsm;
            rasterizer = split ? Class.forName(prefix + "GLSMRasterizer", false, loader) : glsm;
            textures = split ? Class.forName(prefix + "GLSMTextures", false, loader) : glsm;
            vertexInput = split ? Class.forName(prefix + "GLSMVertexInput", false, loader) : glsm;
            buffers = split ? Class.forName(prefix + "GLSMBuffers", false, loader) : glsm;
            currentAttribs = split ? Class.forName(prefix + "GLSMCurrentAttribs", false, loader) : glsm;
            attribStack = split ? Class.forName(prefix + "GLSMAttribStack", false, loader) : glsm;
            programs = split ? Class.forName(prefix + "GLSMPrograms", false, loader) : glsm;
            framebuffer = split ? Class.forName(prefix + "GLSMFramebuffer", false, loader) : glsm;
            texEnvGen = split ? Class.forName(prefix + "GLSMTexEnvGen", false, loader) : glsm;
            draws = split ? Class.forName(prefix + "GLSMDraws", false, loader) : glsm;
            // 两个已核 profile 都须具有准确 primitive/buffer ABI，未知结构在绑定处直接失败。
            method("glGetFloat", int.class, java.nio.FloatBuffer.class);
            method("glGetInteger", int.class);
            method("glGetInteger", int.class, java.nio.IntBuffer.class);
            method("glGetBoolean", int.class, java.nio.ByteBuffer.class);
            method("glIsEnabled", int.class);
            method("glMatrixMode", int.class);
            method("glLoadMatrix", java.nio.FloatBuffer.class);
        }

        /** 只路由现有 consumer 已核的操作，不枚举 owner 或换用 driver。 */
        public java.lang.reflect.Method method(String name, Class<?>... parameters) throws NoSuchMethodException {
            Class<?> owner;
            switch (name) {
                case "glGetFloat": case "glGetInteger": case "glGetBoolean": owner = queries; break;
                case "glIsEnabled": case "glEnable": case "glDisable": owner = enable; break;
                case "glMatrixMode": case "glLoadMatrix": case "glPopMatrix":
                case "getMatrixStack": case "getMatrixStackDepth": owner = matrices; break;
                case "glBindTexture": case "glActiveTexture": case "glClientActiveTexture": owner = textures; break;
                case "glBindVertexArray": case "glVertexAttribPointer": case "glVertexAttribIPointer":
                case "glEnableVertexAttribArray": case "glDisableVertexAttribArray":
                case "glDisableClientState": owner = vertexInput; break;
                case "glBindBuffer": owner = buffers; break;
                case "glColor4f": case "glNormal3f": owner = currentAttribs; break;
                case "glPushAttrib": case "glPopAttrib": case "glPushClientAttrib":
                case "glPopClientAttrib": case "getAttribDepth": owner = attribStack; break;
                case "glUseProgram": owner = programs; break;
                case "glBindFramebuffer": owner = framebuffer; break;
                case "glTexEnvi": owner = texEnvGen; break;
                case "glAlphaFunc": owner = depthStencilAlpha; break;
                case "preDraw": owner = draws; break;
                default: throw new NoSuchMethodException("未声明的Angelica状态操作: " + name);
            }
            java.lang.reflect.Method method = owner.getMethod(name, parameters);
            String result = "glIsEnabled".equals(name) ? "boolean"
                    : "glGetInteger".equals(name) && parameters.length == 1
                            || "getAttribDepth".equals(name) || "getMatrixStackDepth".equals(name) ? "int"
                    : "getMatrixStack".equals(name) ? "org.joml.Matrix4fStack" : "void";
            if (!java.lang.reflect.Modifier.isStatic(method.getModifiers())
                    || !result.equals(method.getReturnType().getName()))
                throw new NoSuchMethodException("Angelica状态入口签名不匹配: " + method);
            return method;
        }
    }
    public static String select(ClassLoader loader) {
        String owner = "com/gtnewhorizons/angelica/client/font/BatchingFontRenderer";
        if (loader.getResource(owner + ".class") == null) return null;
        String args = "(L" + owner + ";[L" + owner + "$FontDrawCmd;IIIFFF)";
        try {
            if (SceneNativeInputSupport.member(loader, owner + ".class", true, "emitRangeThroughPipeline", args + "Z", 0x0008)) return "v2210";
            if (SceneNativeInputSupport.member(loader, owner + ".class", true, "emitRangeThroughPipeline", args + "V", 0x0008)) return "v2228";
        } catch (java.io.IOException failure) { throw new IllegalStateException("无法读取Angelica ABI", failure); }
        throw new IllegalStateException("Angelica存在但pipeline helper ABI不受支持");
    }
}
