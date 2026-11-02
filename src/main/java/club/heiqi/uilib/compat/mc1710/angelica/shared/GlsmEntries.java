package club.heiqi.uilib.compat.mc1710.angelica.shared;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/** Angelica有效状态与GL回放入口的反射绑定；入口归属由各版本Lookup声明。
 * 2.2.10/2.2.28全部在GLStateManager；2.2.40+拆到GLSM*分组类，因此每个入口先取拆分Class、缺失再回退GLStateManager。
 * 构造只解析入口、不触发目标静态初始化；两处都缺席即真实ABI不匹配，显式失败而不假装driver等价。 */
public abstract class GlsmEntries {
    /** 入口分组；Lookup按版本返回其归属类。 */
    public enum Kind {
        GLSM,
        DRAWING,
        QUERIES,
        ENABLE,
        DEPTH_STENCIL_ALPHA,
        BLEND,
        RASTERIZER,
        TEXTURES,
        MATRICES,
        VERTEX_INPUT,
        BUFFERS,
        CURRENT_ATTRIBS
    }

    public interface Lookup {
        /** 返回该入口在目标版本中的归属类；尚未拆分出去的入口返回null，绑定回退GLStateManager。 */
        Class<?> type(ClassLoader loader, Kind kind) throws ClassNotFoundException;
    }

    public final Method getFloat,
            getInteger,
            getIntegers,
            getBooleans,
            isEnabled,
            getAlpha,
            alphaEnabled,
            depthWrite,
            getStencil,
            getPolygon,
            getTextures,
            effectiveBlend,
            effectiveBlendEnabled,
            caching,
            getNormal,
            lightingFactor,
            blockEntity,
            matrixMode,
            loadMatrix,
            bindVao,
            bindBuffer,
            pointer,
            integerPointer,
            enableAttrib,
            disableAttrib,
            bindTexture,
            activeTexture,
            color,
            normalSetter,
            setFlags,
            alphaFunction,
            alphaReference,
            offsetFactor,
            offsetUnits,
            unitMatrix,
            matrixGet,
            unitBinding,
            bindingValue,
            unitState,
            unitEnabled;
    public final Method[] blendGetters = new Method[10], backStencilGetters = new Method[7];
    public final Constructor<?> alphaConstructor, blendConstructor, vectorConstructor;
    public final Field vectorX, vectorY, vectorZ, instance;
    public final LightmapState lightmap;

    protected GlsmEntries(ClassLoader loader, Lookup lookup) {
        try {
            final Class<?> glsm = lookup.type(loader, Kind.GLSM);
            if (glsm == null) throw new IllegalStateException("Angelica GLStateManager缺席");
            final Class<?> queries = lookup.type(loader, Kind.QUERIES),
                    enable = lookup.type(loader, Kind.ENABLE),
                    depthStencilAlpha = lookup.type(loader, Kind.DEPTH_STENCIL_ALPHA),
                    blendClass = lookup.type(loader, Kind.BLEND),
                    rasterizer = lookup.type(loader, Kind.RASTERIZER),
                    texturesClass = lookup.type(loader, Kind.TEXTURES),
                    matrices = lookup.type(loader, Kind.MATRICES),
                    vertexInput = lookup.type(loader, Kind.VERTEX_INPUT),
                    buffers = lookup.type(loader, Kind.BUFFERS),
                    attribs = lookup.type(loader, Kind.CURRENT_ATTRIBS);
            getFloat = method(queries, glsm, "glGetFloat", int.class, FloatBuffer.class);
            getInteger = method(queries, glsm, "glGetInteger", int.class);
            getIntegers = method(queries, glsm, "glGetInteger", int.class, IntBuffer.class);
            getBooleans = method(queries, glsm, "glGetBoolean", int.class, ByteBuffer.class);
            isEnabled = method(enable, glsm, "glIsEnabled", int.class);
            Class<?> blend =
                    Class.forName("com.gtnewhorizons.angelica.glsm.states.BlendState", false, loader);
            blendConstructor = blend.getConstructor();
            effectiveBlend = method(blendClass, glsm, "getEffectiveBlendState", blend);
            effectiveBlendEnabled = method(blendClass, glsm, "isEffectiveBlendEnabled");
            String[] getters = {
                "getSrcRgb",
                "getDstRgb",
                "getSrcAlpha",
                "getDstAlpha",
                "getEquationRgb",
                "getEquationAlpha",
                "getBlendColorR",
                "getBlendColorG",
                "getBlendColorB",
                "getBlendColorA"
            };
            for (int i = 0; i < getters.length; i++) blendGetters[i] = blend.getMethod(getters[i]);
            caching = glsm.getMethod("isCachingEnabled");
            getStencil = method(depthStencilAlpha, glsm, "getStencilState");
            getPolygon = method(rasterizer, glsm, "getPolygonState");
            String[] stencilGetters = {
                "getFuncBack",
                "getRefBack",
                "getValueMaskBack",
                "getWriteMaskBack",
                "getFailOpBack",
                "getZFailOpBack",
                "getZPassOpBack"
            };
            for (int i = 0; i < stencilGetters.length; i++)
                backStencilGetters[i] = getStencil.getReturnType().getMethod(stencilGetters[i]);
            offsetFactor = getPolygon.getReturnType().getMethod("getOffsetFactor");
            offsetUnits = getPolygon.getReturnType().getMethod("getOffsetUnits");
            Class<?> alpha =
                    Class.forName("com.gtnewhorizons.angelica.glsm.states.AlphaState", false, loader);
            alphaConstructor = alpha.getConstructor();
            getAlpha = method(depthStencilAlpha, glsm, "getEffectiveAlphaState", alpha);
            alphaFunction = alpha.getMethod("getFunction");
            alphaReference = alpha.getMethod("getReference");
            alphaEnabled = method(depthStencilAlpha, glsm, "isEffectiveAlphaTestEnabled");
            depthWrite = method(depthStencilAlpha, glsm, "isEffectiveDepthMaskEnabled");
            Class<?> shader =
                    Class.forName("com.gtnewhorizons.angelica.glsm.ffp.ShaderManager", false, loader);
            getNormal = shader.getMethod("getCurrentNormal");
            Class<?> vector = getNormal.getReturnType();
            vectorX = vector.getField("x");
            vectorY = vector.getField("y");
            vectorZ = vector.getField("z");
            vectorConstructor = vector.getConstructor();
            getTextures = method(texturesClass, glsm, "getTextures");
            Class<?> textures = getTextures.getReturnType();
            unitMatrix = textures.getMethod("getTextureUnitMatrix", int.class);
            matrixGet = unitMatrix.getReturnType().getMethod("get", FloatBuffer.class);
            unitBinding = textures.getMethod("getTextureUnitBindings", int.class);
            bindingValue = unitBinding.getReturnType().getMethod("getBinding");
            unitState = textures.getMethod("getTextureUnitStates", int.class);
            unitEnabled = unitState.getReturnType().getMethod("isEnabled");
            lightmap = LightmapState.bind(loader);
            Class<?> captured =
                    Class.forName("net.coderbot.iris.uniforms.CapturedRenderingState", false, loader);
            instance = captured.getField("INSTANCE");
            blockEntity = captured.getMethod("getCurrentRenderedBlockEntity");
            Class<?> lighting =
                    Class.forName(
                            "com.gtnewhorizons.angelica.glsm.ffp.FFPVertexLighting", false, loader);
            lightingFactor = lighting.getMethod("modulatesVertexColor", vector);
            matrixMode = method(matrices, glsm, "glMatrixMode", int.class);
            loadMatrix = method(matrices, glsm, "glLoadMatrix", FloatBuffer.class);
            bindVao = method(vertexInput, glsm, "glBindVertexArray", int.class);
            bindBuffer = method(buffers, glsm, "glBindBuffer", int.class, int.class);
            pointer =
                    method(
                            vertexInput,
                            glsm,
                            "glVertexAttribPointer",
                            int.class,
                            int.class,
                            int.class,
                            boolean.class,
                            int.class,
                            long.class);
            integerPointer =
                    method(
                            vertexInput,
                            glsm,
                            "glVertexAttribIPointer",
                            int.class,
                            int.class,
                            int.class,
                            int.class,
                            long.class);
            enableAttrib = method(vertexInput, glsm, "glEnableVertexAttribArray", int.class);
            disableAttrib = method(vertexInput, glsm, "glDisableVertexAttribArray", int.class);
            bindTexture = method(texturesClass, glsm, "glBindTexture", int.class, int.class);
            activeTexture = method(texturesClass, glsm, "glActiveTexture", int.class);
            color =
                    method(
                            attribs,
                            glsm,
                            "glColor4f",
                            float.class,
                            float.class,
                            float.class,
                            float.class);
            normalSetter =
                    method(attribs, glsm, "glNormal3f", float.class, float.class, float.class);
            Class<?> vaoManager =
                    Class.forName("com.gtnewhorizons.angelica.glsm.ffp.VAOManager", false, loader);
            setFlags = vaoManager.getMethod("setCurrentVertexFlags", int.class);
        } catch (ReflectiveOperationException | LinkageError error) {
            throw new IllegalStateException("Angelica GLSM入口ABI不匹配", error);
        }
    }

    /** 优先用拆分后的GLSM*入口，尚未拆分时回退GLStateManager；两处都缺席才是真实ABI不匹配。 */
    static Method method(Class<?> split, Class<?> glsm, String name, Class<?>... parameters)
            throws NoSuchMethodException {
        if (split != null) {
            try {
                return split.getMethod(name, parameters);
            } catch (NoSuchMethodException notSplitYet) {
                // 迁移中的版本可能只挪走部分入口，继续按旧位置解析。
            }
        }
        return glsm.getMethod(name, parameters);
    }
}
