package club.heiqi.uilib.compat.mc1710.angelica.shared;

import club.heiqi.uilib.host.client.font.compat.*;
import club.heiqi.uilib.host.client.font.context.*;
import club.heiqi.uilib.host.client.gl.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import club.heiqi.uilib.host.client.gl.GlApi;
import club.heiqi.uilib.host.client.gl.GlTokens;
import club.heiqi.uilib.host.client.font.context.FontRenderContext.MatrixState;

/** 可选Angelica有效状态读取，仅缓存稳定访问入口、不持mutable JOML或GL上下文实例。
 * 固定来源：GLSM glGetFloat(int,FloatBuffer)、getEffectiveAlphaState、texture-unit1 matrix；
 * raster/viewport/depth/scissor统一经GLSM有效query；无缓存特例的枚举由GLSM回退driver。
 * 存在但不匹配时显式失败，不假装driver等价。
 * provider不拥有GL/字体对象；构造只解析入口，capture在宿主GL线程执行。 */
public final class AngelicaMatrixStateProvider
        implements LwjglFontContextCapture.MatrixStateProvider {
    private final GlApi gl;
    // 同一provider只在所属GL线程同步查询；三种view共用有界scratch，结果在下一次查询前复制。
    private final ByteBuffer queryBytes = ByteBuffer.allocateDirect(1024).order(ByteOrder.nativeOrder());
    private final FloatBuffer queryFloats = queryBytes.asFloatBuffer();
    private final IntBuffer queryIntegers = queryBytes.asIntBuffer();

    @Override
    public GlApi gl() {
        return gl;
    }

    private final MethodHandle getFloat,
            getAlpha,
            alphaEnabled,
            depthWrite,
            getNormal,
            getTextures,
            unitMatrix,
            unitBinding,
            bindingValue,
            matrixGet,
            blockEntity,
            lightingFactor,
            unitState,
            unitEnabled;
    private final MethodHandle alphaConstructor, vectorConstructor;
    private final MethodHandle alphaFunction, alphaReference;
    private final MethodHandle getInteger,
            getIntegers,
            getBooleans,
            isEnabled,
            effectiveBlendEnabled,
            effectiveBlend;
    private final MethodHandle blendConstructor;
    private final MethodHandle[] blendGetters = new MethodHandle[10], backStencilGetters = new MethodHandle[7];
    private final MethodHandle caching, getStencil, getPolygon, offsetFactor, offsetUnits;
    private static final int[] BACK_STENCIL_NAMES = {
        GlTokens.GL_STENCIL_BACK_FUNC,
        GlTokens.GL_STENCIL_BACK_REF,
        GlTokens.GL_STENCIL_BACK_VALUE_MASK,
        GlTokens.GL_STENCIL_BACK_WRITEMASK,
        GlTokens.GL_STENCIL_BACK_FAIL,
        GlTokens.GL_STENCIL_BACK_PASS_DEPTH_FAIL,
        GlTokens.GL_STENCIL_BACK_PASS_DEPTH_PASS
    };
    private final MethodHandle vectorX, vectorY, vectorZ, instance;
    private final LightmapState lightmap;

    public static LwjglFontContextCapture.MatrixStateProvider optional(
            ClassLoader loader, GlApi gl) {
        final Class<?> glsm;
        try {
            glsm = Class.forName("com.gtnewhorizons.angelica.glsm.GLStateManager", false, loader);
        } catch (ClassNotFoundException absent) {
            return LwjglFontContextCapture.compatibility(gl);
        } catch (LinkageError mismatch) {
            throw new IllegalStateException("Angelica有效状态类无法加载，不能回退legacy矩阵", mismatch);
        }
        return new AngelicaMatrixStateProvider(glsm, loader, gl);
    }

    private AngelicaMatrixStateProvider(Class<?> glsm, ClassLoader loader, GlApi gl) {
        this.gl = java.util.Objects.requireNonNull(gl, "gl");
        try {
            getFloat = bind(glsm.getMethod("glGetFloat", int.class, FloatBuffer.class),
                    void.class, int.class, FloatBuffer.class);
            getInteger = bind(glsm.getMethod("glGetInteger", int.class), int.class, int.class);
            getIntegers = bind(glsm.getMethod("glGetInteger", int.class, IntBuffer.class),
                    void.class, int.class, IntBuffer.class);
            getBooleans = bind(glsm.getMethod("glGetBoolean", int.class, ByteBuffer.class),
                    void.class, int.class, ByteBuffer.class);
            isEnabled = bind(glsm.getMethod("glIsEnabled", int.class), boolean.class, int.class);
            Class<?> blend =
                    Class.forName(
                            "com.gtnewhorizons.angelica.glsm.states.BlendState", false, loader);
            blendConstructor = constructor(blend);
            effectiveBlend = bind(glsm.getMethod("getEffectiveBlendState", blend), Object.class, Object.class);
            effectiveBlendEnabled = bind(glsm.getMethod("isEffectiveBlendEnabled"), boolean.class);
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
            for (int i = 0; i < getters.length; i++)
                blendGetters[i] = bind(blend.getMethod(getters[i]), i < 6 ? int.class : float.class, Object.class);
            caching = bind(glsm.getMethod("isCachingEnabled"), boolean.class);
            Method stencil = glsm.getMethod("getStencilState"), polygon = glsm.getMethod("getPolygonState");
            getStencil = bind(stencil, Object.class);
            getPolygon = bind(polygon, Object.class);
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
                backStencilGetters[i] = bind(stencil.getReturnType().getMethod(stencilGetters[i]),
                        int.class, Object.class);
            offsetFactor = bind(polygon.getReturnType().getMethod("getOffsetFactor"), float.class, Object.class);
            offsetUnits = bind(polygon.getReturnType().getMethod("getOffsetUnits"), float.class, Object.class);
            Class<?> alpha =
                    Class.forName(
                            "com.gtnewhorizons.angelica.glsm.states.AlphaState", false, loader);
            alphaConstructor = constructor(alpha);
            // 此入口填充dest；校验返回引用后仅丢弃返回值，仍读取本次新建的dest。
            getAlpha = bind(glsm.getMethod("getEffectiveAlphaState", alpha), Object.class, Object.class)
                    .asType(MethodType.methodType(void.class, Object.class));
            alphaFunction = bind(alpha.getMethod("getFunction"), int.class, Object.class);
            alphaReference = bind(alpha.getMethod("getReference"), float.class, Object.class);
            alphaEnabled = bind(glsm.getMethod("isEffectiveAlphaTestEnabled"), boolean.class);
            depthWrite = bind(glsm.getMethod("isEffectiveDepthMaskEnabled"), boolean.class);
            Class<?> shader =
                    Class.forName(
                            "com.gtnewhorizons.angelica.glsm.ffp.ShaderManager", false, loader);
            Method normal = shader.getMethod("getCurrentNormal");
            getNormal = bind(normal, Object.class);
            Class<?> vector = normal.getReturnType();
            vectorX = getter(vector.getField("x"), float.class, Object.class);
            vectorY = getter(vector.getField("y"), float.class, Object.class);
            vectorZ = getter(vector.getField("z"), float.class, Object.class);
            vectorConstructor = constructor(vector);
            Method textureArray = glsm.getMethod("getTextures");
            getTextures = bind(textureArray, Object.class);
            Class<?> textures = textureArray.getReturnType();
            Method matrix = textures.getMethod("getTextureUnitMatrix", int.class);
            unitMatrix = bind(matrix, Object.class, Object.class, int.class);
            matrixGet = bind(matrix.getReturnType().getMethod("get", FloatBuffer.class),
                    FloatBuffer.class, Object.class, FloatBuffer.class)
                    .asType(MethodType.methodType(void.class, Object.class, FloatBuffer.class));
            Method binding = textures.getMethod("getTextureUnitBindings", int.class);
            unitBinding = bind(binding, Object.class, Object.class, int.class);
            bindingValue = bind(binding.getReturnType().getMethod("getBinding"), int.class, Object.class);
            Method state = textures.getMethod("getTextureUnitStates", int.class);
            unitState = bind(state, Object.class, Object.class, int.class);
            unitEnabled = bind(state.getReturnType().getMethod("isEnabled"), boolean.class, Object.class);
            lightmap = LightmapState.bind(loader);
            Class<?> captured =
                    Class.forName(
                            "net.coderbot.iris.uniforms.CapturedRenderingState", false, loader);
            instance = getter(captured.getField("INSTANCE"), Object.class);
            blockEntity = bind(captured.getMethod("getCurrentRenderedBlockEntity"), int.class, Object.class);
            Class<?> lighting =
                    Class.forName(
                            "com.gtnewhorizons.angelica.glsm.ffp.FFPVertexLighting", false, loader);
            lightingFactor = bind(lighting.getMethod("modulatesVertexColor", vector), boolean.class, Object.class);
        } catch (ReflectiveOperationException | LinkageError error) {
            throw new IllegalStateException("Angelica有效状态ABI不匹配", error);
        }
    }

    // 外部入口只用publicLookup；引用擦除到Object，primitive与buffer必须与真实ABI完全一致。
    // asType本身允许装箱/数值转换，先检查可防止不匹配ABI在热路径悄悄引入这些转换。
    private static MethodHandle exact(MethodHandle handle, Class<?> result, Class<?>... parameters)
            throws NoSuchMethodException {
        MethodType source = handle.type(), target = MethodType.methodType(result, parameters);
        boolean matches = compatible(source.returnType(), result)
                && source.parameterCount() == parameters.length;
        for (int i = 0; matches && i < parameters.length; i++)
            matches = compatible(source.parameterType(i), parameters[i]);
        if (!matches) throw new NoSuchMethodException("Angelica访问入口签名不匹配: " + source + " -> " + target);
        return handle.asType(target);
    }

    private static boolean compatible(Class<?> source, Class<?> target) {
        return source == target || target == Object.class && !source.isPrimitive();
    }

    private static MethodHandle bind(Method method, Class<?> result, Class<?>... parameters)
            throws ReflectiveOperationException {
        return exact(MethodHandles.publicLookup().unreflect(method), result, parameters);
    }

    private static MethodHandle constructor(Class<?> type) throws ReflectiveOperationException {
        return exact(MethodHandles.publicLookup().unreflectConstructor(type.getConstructor()), Object.class);
    }

    private static MethodHandle getter(Field field, Class<?> result, Class<?>... parameters)
            throws ReflectiveOperationException {
        return exact(MethodHandles.publicLookup().unreflectGetter(field), result, parameters);
    }

    private static Throwable checkedFailure(Throwable error) {
        // 未检查异常原样传播；checked 失败由所属查询补充边界诊断。
        if (error instanceof RuntimeException) throw (RuntimeException) error;
        if (error instanceof Error) throw (Error) error;
        return error;
    }

    @Override
    public MatrixState capture() {
        try {
            float[] p = read(GlTokens.GL_PROJECTION_MATRIX, 16),
                    mv = read(GlTokens.GL_MODELVIEW_MATRIX, 16),
                    color = read(GlTokens.GL_CURRENT_COLOR, 4);
            Object alpha = (Object) alphaConstructor.invokeExact();
            getAlpha.invokeExact(alpha);
            Object textures = (Object) getTextures.invokeExact();
            int texture =
                    (int) bindingValue.invokeExact((Object) unitBinding.invokeExact(textures, 1));
            // 专用宿主font判据：BatchingFontRenderer.isWorldSpaceText以m33()==0识别透视。
            // 它不是通用world分类；custom projection应使用显式MatrixStateProvider。
            boolean lit =
                    p[15] == 0
                            && texture != 0
                            && (boolean) unitEnabled.invokeExact((Object) unitState.invokeExact(textures, 1));
            float u = 0, v = 0;
            int packed = 0;
            if (lit) {
                float[] m = new float[16];
                FloatBuffer out = floats(16);
                matrixGet.invokeExact((Object) unitMatrix.invokeExact(textures, 1), out);
                for (int i = 0; i < 16; i++) m[i] = out.get(i);
                float x = lightmap.x(), y = lightmap.y();
                u = m[0] * x + m[4] * y + m[12];
                v = m[1] * x + m[5] * y + m[13];
                packed = lightmap.packed();
            }
            Object n = (Object) getNormal.invokeExact();
            float[] normal = vector(n);
            float[] factor = {1, 1, 1};
            if (p[15] == 0) {
                Object dest = (Object) vectorConstructor.invokeExact();
                if ((boolean) lightingFactor.invokeExact(dest)) factor = vector(dest);
            }
            return new MatrixState(
                    p,
                    mv,
                    color,
                    (boolean) alphaEnabled.invokeExact(),
                    (int) alphaFunction.invokeExact(alpha),
                    (float) alphaReference.invokeExact(alpha),
                    lit,
                    lit ? texture : 0,
                    u,
                    v,
                    packed,
                    normal,
                    factor,
                    (int) blockEntity.invokeExact((Object) instance.invokeExact()));
        } catch (Throwable error) {
            throw new IllegalStateException("Angelica有效context捕捉失败；未使用driver假矩阵",
                    checkedFailure(error));
        }
    }

    @Override
    public boolean depthWrite() {
        try {
            return (boolean) depthWrite.invokeExact();
        } catch (Throwable error) {
            throw new IllegalStateException("Angelica effective depthWrite读取失败", checkedFailure(error));
        }
    }

    @Override
    public int integer(int name) {
        try {
            // 仅背面模板状态需要选择有效 getter；其他枚举直接交给 GLSM。
            for (int i = 0; i < BACK_STENCIL_NAMES.length; i++)
                if (name == BACK_STENCIL_NAMES[i] && (boolean) caching.invokeExact())
                    return (int) backStencilGetters[i].invokeExact((Object) getStencil.invokeExact());
            return (int) getInteger.invokeExact(name);
        } catch (Throwable error) {
            throw failure(error);
        }
    }

    @Override
    public int[] integers(int name, int count) {
        try {
            checkCount(count, queryIntegers.capacity());
            queryIntegers.clear();
            queryIntegers.limit(Math.max(16, count));
            IntBuffer out = queryIntegers;
            getIntegers.invokeExact(name, out);
            int[] a = new int[count];
            for (int i = 0; i < count; i++) a[i] = out.get(i);
            return a;
        } catch (Throwable error) {
            throw failure(error);
        }
    }

    @Override
    public boolean enabled(int name) {
        try {
            return (boolean) isEnabled.invokeExact(name);
        } catch (Throwable error) {
            throw failure(error);
        }
    }

    @Override
    public boolean[] booleans(int name, int count) {
        try {
            checkCount(count, queryBytes.capacity());
            queryBytes.clear();
            queryBytes.limit(Math.max(16, count));
            ByteBuffer out = queryBytes;
            getBooleans.invokeExact(name, out);
            boolean[] a = new boolean[count];
            for (int i = 0; i < count; i++) a[i] = out.get(i) != 0;
            return a;
        } catch (Throwable error) {
            throw failure(error);
        }
    }

    @Override
    public float[] floats(int name, int count) {
        try {
            if (count == 1
                    && (name == GlTokens.GL_POLYGON_OFFSET_FACTOR
                            || name == GlTokens.GL_POLYGON_OFFSET_UNITS)
                    && (boolean) caching.invokeExact()) {
                MethodHandle getter = name == GlTokens.GL_POLYGON_OFFSET_FACTOR ? offsetFactor : offsetUnits;
                return new float[] {(float) getter.invokeExact((Object) getPolygon.invokeExact())};
            }
            return read(name, count);
        } catch (Throwable error) {
            throw failure(error);
        }
    }

    @Override
    public FontRenderContext.RasterState rasterState() {
        // front stencil/colorMask/depthRange/viewport/cull走GLSM query缓存；back stencil/offset
        // glGet未覆盖，额外读取公开state getter（caching关闭则仍走GLSM query）。
        // scissor box的宿主setter直接写driver，query真实fallback。Iris锁blend须取readEffective。
        try {
            Object blend = (Object) effectiveBlend.invokeExact((Object) blendConstructor.invokeExact());
            int[] values = new int[6];
            float[] color = new float[4];
            for (int i = 0; i < 6; i++)
                values[i] = (int) blendGetters[i].invokeExact(blend);
            for (int i = 0; i < 4; i++)
                color[i] = (float) blendGetters[i + 6].invokeExact(blend);
            // blend 使用宿主有效快照，其余光栅状态沿平台捕获。
            return EffectiveBlendCapture.capture(this, (boolean) effectiveBlendEnabled.invokeExact(),
                    values[0], values[1], values[2], values[3], values[4], values[5], color);
        } catch (Throwable error) {
            throw failure(error);
        }
    }

    private FloatBuffer floats(int count) {
        checkCount(count, queryFloats.capacity());
        queryFloats.clear();
        // GLSM未缓存的枚举仍可进入LWJGL2；小向量查询也必须保留至少16个元素。
        queryFloats.limit(Math.max(16, count));
        return queryFloats;
    }

    private static void checkCount(int count, int capacity) {
        if (count < 0 || count > capacity) throw new IllegalArgumentException("有效状态buffer越界");
    }

    private static IllegalStateException failure(Throwable error) {
        return new IllegalStateException("Angelica有效光栅状态读取失败；未降级driver", checkedFailure(error));
    }

    private float[] vector(Object v) throws Throwable {
        return new float[] {(float) vectorX.invokeExact(v), (float) vectorY.invokeExact(v), (float) vectorZ.invokeExact(v)};
    }

    private float[] read(int name, int length) throws Throwable {
        FloatBuffer buffer = floats(length);
        getFloat.invokeExact(name, buffer);
        float[] result = new float[length];
        for (int i = 0; i < length; i++) result[i] = buffer.get(i);
        return result;
    }
}
