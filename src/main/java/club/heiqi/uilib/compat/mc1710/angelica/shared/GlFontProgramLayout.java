package club.heiqi.uilib.compat.mc1710.angelica.shared;

import club.heiqi.uilib.host.client.font.compat.*;
import club.heiqi.uilib.host.client.font.context.*;
import club.heiqi.uilib.host.client.gl.*;

import static club.heiqi.uilib.host.client.gl.GlTokens.*;

import java.util.ArrayList;
import java.util.List;

/** 按当前 pack program 的活动属性建立映射，未知或失配属性明确报错；映射随本次 program 使用。 */
final class GlFontProgramLayout {
    static final int FLOATS = 33, STRIDE = FLOATS * 4;

    static final class Binding {
        final int location, size, offset;

        Binding(int location, int size, int offset) {
            this.location = location;
            this.size = size;
            this.offset = offset;
        }
    }

    final List<Binding> bindings = new ArrayList<Binding>();
    boolean uv, color, lightmap, normal;

    GlFontProgramLayout(List<GlProgramAttribute> attributes, int program) {
        for (GlProgramAttribute a : attributes) {
            if (a.size != 1)
                throw new UnsupportedOperationException(
                        "Iris program " + program + "不支持attribute数组 " + a.name + " size=" + a.size);
            int offset, size;
            if (a.location == 0 && a.type == GL_FLOAT_VEC4) {
                offset = 0;
                size = 4;
            } else if (a.location == 1 && a.type == GL_FLOAT_VEC4) {
                offset = 4;
                size = 4;
                color = true;
            } else if (a.location == 2 && a.type == GL_FLOAT_VEC4) {
                offset = 8;
                size = 4;
                uv = true;
            } else if (a.location == 3 && a.type == GL_FLOAT_VEC4) {
                offset = 12;
                size = 4;
                lightmap = true;
            } else if (a.location == 4 && a.type == GL_FLOAT_VEC3) {
                offset = 16;
                size = 3;
                normal = true;
            } else if ("mc_midTexCoord".equals(a.name)
                    && (a.type == GL_FLOAT_VEC2 || a.type == GL_FLOAT_VEC3 || a.type == GL_FLOAT_VEC4)) {
                // BSL 声明 vec4，Angelica 非 terrain 输入提供 XY；指针绑定两个分量，
                // 由 GL 补 Z=0、W=1，后续 tangent 字段保持独立。
                offset = 19;
                size = 2;
            } else if ("at_tangent".equals(a.name) && a.type == GL_FLOAT_VEC4) {
                offset = 21;
                size = 4;
            } else if ("mc_Entity".equals(a.name)
                    && (a.type == GL_FLOAT_VEC2
                            || a.type == GL_FLOAT_VEC3
                            || a.type == GL_FLOAT_VEC4)) {
                offset = 25;
                size = a.type == GL_FLOAT_VEC2 ? 2 : a.type == GL_FLOAT_VEC3 ? 3 : 4;
            } else if ("at_midBlock".equals(a.name)
                    && (a.type == GL_FLOAT_VEC3 || a.type == GL_FLOAT_VEC4)) {
                offset = 29;
                size = a.type == GL_FLOAT_VEC3 ? 3 : 4;
            } else
                throw new UnsupportedOperationException(
                        "Iris program "
                                + program
                                + "未知或不匹配attribute "
                                + a.name
                                + " location="
                                + a.location
                                + " type="
                                + a.type
                                + " size="
                                + a.size);
            bindings.add(new Binding(a.location, size, offset * 4));
        }
    }
}
