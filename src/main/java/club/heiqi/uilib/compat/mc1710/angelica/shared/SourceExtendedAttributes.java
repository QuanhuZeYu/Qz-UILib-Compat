package club.heiqi.uilib.compat.mc1710.angelica.shared;

import club.heiqi.uilib.host.client.gl.GlApi;
import club.heiqi.uilib.host.client.gl.GlTokens;
import java.util.Arrays;

/** 保存 Angelica 非 terrain 字体的 FFP generic 来源值副本，atlas、VAO 与 native buffer 由各自 owner 管理。
 * GbufferPrograms.setBlockEntityDefaults 写入 attr11=(-1,-1,0,1)、attr14=(0,0,0,0)。
 * ImmediateExtendedAttribHandler 生成 12/midUV、13/tangent；entity/item/block ID 归 uniform，mc_Entity 保留来源 current value。
 * at_midBlock 同样读取来源 current value；terrain 的 uint packed mcEntity 使用独立 ABI。
 * GLSM generic setter 直达 backend，因此 glGetVertexAttribfv 提供实际来源事实。 */
public final class SourceExtendedAttributes {
    private final float[] mcEntity, midBlock;

    private SourceExtendedAttributes(float[] mcEntity, float[] midBlock) {
        this.mcEntity = copy(mcEntity);
        this.midBlock = copy(midBlock);
    }

    private static float[] copy(float[] value) {
        float[] result = value.clone();
        for (float x : result)
            if (!Float.isFinite(x)) throw new IllegalStateException("非有限来源extended属性");
        return result;
    }

    static SourceExtendedAttributes capture(GlApi gl) {
        return new SourceExtendedAttributes(current(gl, 11), current(gl, 14));
    }

    private static float[] current(GlApi gl, int location) {
        float[] values = new float[4];
        gl.glGetVertexAttribfv(location, GlTokens.GL_CURRENT_VERTEX_ATTRIB, values);
        return values;
    }

    /** 直接比较已捕获的有限来源值。 */
    boolean sameValues(SourceExtendedAttributes other) {
        return other != null && Arrays.equals(mcEntity, other.mcEntity)
                && Arrays.equals(midBlock, other.midBlock);
    }

    public float[] mcEntity() {
        return mcEntity.clone();
    }

    public float[] midBlock() {
        return midBlock.clone();
    }

    /** 按 FFP 浮点 ABI 提供已知来源值；uint/ivec packed terrain 或额外属性需要独立来源协议，失配时明确失败。 */
    public float[] valuesFor(String name, int shaderType) {
        if (shaderType != GlTokens.GL_FLOAT
                && shaderType != GlTokens.GL_FLOAT_VEC2
                && shaderType != GlTokens.GL_FLOAT_VEC3
                && shaderType != GlTokens.GL_FLOAT_VEC4)
            throw new IllegalStateException(
                    "字体来源FFP不提供packed/integer属性：" + name + " type=" + shaderType);
        if ("mc_Entity".equals(name)) return mcEntity();
        if ("at_midBlock".equals(name)) return midBlock();
        throw new IllegalStateException("未知字体extended属性语义：" + name);
    }
}
