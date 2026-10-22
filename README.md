# Qz-UILib-Compat

Forge 1.7.10 的可选兼容 Mod，**1.0.0-beta1** 配套 **Qz-UILib 5.0.0-beta3**。

## 安装

1. 安装 [Qz-UILib Minecraft 5.0.0-beta3](https://github.com/QuanhuZeYu/Qz-UILib/releases/tag/5.0.0-beta3) 的 Forge 1.7.10 完整安装包。
2. 安装 [UniMixins 0.3.1](https://github.com/LegacyModdingMC/UniMixins/releases/tag/0.3.1) 的 `+unimixins-all-1.7.10-0.3.1.jar`；整合包已提供时保留一份。
3. 从 [1.0.0-beta1 Release](https://github.com/QuanhuZeYu/Qz-UILib-Compat/releases/tag/1.0.0-beta1) 下载 `qz_uilib_compat-1.0.0-beta1.jar`，放入实例的 `mods`，移除旧版 Compat。

Angelica、lwjgl3ify/RFB 和 AE2 均按需使用；只有已安装的目标 Mod 才启用相应适配。

## 适配内容

- Angelica/Iris：字体提交顺序与显示列表回放。
- lwjgl3ify/RFB：增强输入与 LWJGL3 接入。
- AE2：监控面板的字体与缓存更新。
- GTNH：私用字符映射到 Unicode，沿用 UILib 字体渲染。

GTNH 映射默认开启；在 `config/qz_uilib_compat.yaml` 中设置 `gtnh.mapPrivateCharacters`，按 F3+T 或重启生效。

## 开发接入

适配开发见[兼容规格](specs/2026-10-21-compat-spec.md)。

## 许可

自有代码采用 [MIT](LICENSE)；随附组件的[第三方通知](https://github.com/QuanhuZeYu/Qz-UILib-Platform/blob/5.0.0-beta3/THIRD-PARTY-NOTICES.md) 保留原有许可。
