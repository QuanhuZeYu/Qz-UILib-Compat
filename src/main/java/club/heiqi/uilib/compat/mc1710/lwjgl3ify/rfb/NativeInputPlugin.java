package club.heiqi.uilib.compat.mc1710.lwjgl3ify.rfb;

import com.gtnewhorizons.retrofuturabootstrap.api.RfbClassTransformer;
import com.gtnewhorizons.retrofuturabootstrap.api.RfbPlugin;

/** RFB 存在时由其描述资源触发加载；普通 LWJGL2 沿原输入路径运行。 */
public final class NativeInputPlugin implements RfbPlugin {
    @Override public RfbClassTransformer[] makeEarlyTransformers() {
        return new RfbClassTransformer[] {new NativeInputTransformer()};
    }
}
