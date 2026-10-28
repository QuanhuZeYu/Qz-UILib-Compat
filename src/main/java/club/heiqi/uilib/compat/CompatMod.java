package club.heiqi.uilib.compat;

import cpw.mods.fml.common.Mod;

/** 声明可选兼容 Mod 身份并加载配置；渲染服务由宿主初始化，SPI 在首次请求时发现。 */
@Mod(modid = "qz_uilib_compat", name = "Qz UILib Compat", version = CompatVersion.VERSION,
        dependencies = "required-after:qz_uilib", acceptableRemoteVersions = "*")
public final class CompatMod {
    @Mod.EventHandler
    public void preInit(cpw.mods.fml.common.event.FMLPreInitializationEvent event) throws java.io.IOException {
        club.heiqi.uilib.compat.mc1710.gtnh.GtnhCharacterMapping.initialize(
                event.getModConfigurationDirectory().toPath().resolve("qz_uilib_compat.yaml"));
    }
}
