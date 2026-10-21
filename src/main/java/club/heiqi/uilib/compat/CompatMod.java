package club.heiqi.uilib.compat;

import cpw.mods.fml.common.Mod;

/** 可选兼容独立身份；加载顺序不负责渲染服务初始化，SPI随宿主首次请求发现。 */
@Mod(modid = "qz_uilib_compat", name = "Qz UILib Compat", version = CompatVersion.VERSION,
        dependencies = "required-after:qz_uilib", acceptableRemoteVersions = "*")
public final class CompatMod {
    @Mod.EventHandler
    public void preInit(cpw.mods.fml.common.event.FMLPreInitializationEvent event) throws java.io.IOException {
        club.heiqi.uilib.compat.mc1710.gtnh.GtnhCharacterMapping.initialize(
                event.getModConfigurationDirectory().toPath().resolve("qz_uilib_compat.yaml"));
    }
}
