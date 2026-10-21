# Qz-UILib-Compat

可选的 Forge 1.7.10 兼容 Mod，当前版本为 **1.0.0-beta1**，配套 **Qz-UILib 5.0.0-beta3**。目标、兼容边界与安装合同见 [兼容规格](specs/2026-10-21-compat-spec.md)，用户发布说明见 [更新日志](.changelogs/1.0.0-beta1.md)。

## 独立构建

需要 JDK 21、用于字符映射回归的 JDK 8、Python 3，以及可访问公开 Maven 和 GitHub Release 的网络。设置 `UILIB_JDK8` 指向 JDK 8 安装目录；Gradle 由仓内 Wrapper 固定为 9.3.1。

`tools/prepare-upstreams.py` 读取 `gradle.properties` 的 UILib 版本，按精确仓名、同版本 tag 和唯一资产名下载：

| 仓库 | 本次 Release 资产 |
| --- | --- |
| QuanhuZeYu/Qz-UILib-Core | qz-uilib-core-5.0.0-beta3-maven.zip |
| QuanhuZeYu/Qz-UILib-Platform | qz-uilib-platform-5.0.0-beta3-maven.zip |
| QuanhuZeYu/Qz-UILib | qz-uilib-minecraft-5.0.0-beta3-maven.zip |

ZIP 直接包含标准 `asia/quanhuzeyu/uilib/…` Maven 布局。Minecraft 接口坐标为 `asia.quanhuzeyu.uilib:minecraft-host:5.0.0-beta3:development-java8`。准备脚本检查布局、版本、必需接口/POM 和上游覆盖冲突；缺件时明确报告 Core → Platform → Minecraft → Compat 的发布顺序，不使用工作站产物。

```sh
python tools/prepare-upstreams.py
./gradlew build releaseAssets --no-daemon --console=plain
```

Windows 可使用 `gradlew.bat`。默认 Maven 目录为本仓 `build/ci/maven`；保留显式 `-PuilibRepository=<Maven URL>`、`-PuilibVersion` 与 `-PuilibHostVersion`。如本机安装了工作站 Gradle init，请通过 `--gradle-user-home <独立目录>` 排除其注入，再准备并构建。

默认完整 `build` 保留 Java8 字节码、生产 Jar 边界、缺席加载、回放、字符映射/真实 FreeType 与 HarfBuzz、RFB 无窗口回归。真实 ABI 检查从 GTNH 公开 Maven 仓库 `https://nexus.gtnewhorizons.com/repository/public/` 获取无 classifier 的正式 Jar，不以 dev 或 API Jar 替代：

- `com.github.GTNewHorizons:Angelica:2.2.10` 与 `2.2.28`
- `com.github.GTNewHorizons:Applied-Energistics-2-Unofficial:rv3-beta-1050-GTNH` 与 `rv3-beta-1080-GTNH`
- `io.github.legacymoddingmc:unimixins:0.3.1`

实际输入身份和 SHA256 写入 `build/reports/abi-inputs.json`，上游 ZIP 来源与 SHA256 写入 `build/ci/upstreams.json`。旧 `-PcompatPrismRoot=<实例根目录>` 保留为显式实例输入；不传时也必须运行真实 Jar ABI 检查。窗口/GPU 验证使用既有 `deviceCheck`、`irisPassTargetScenario` 等显式任务，自动构建通过不代表游戏实测成功。

## CI 与发布

分支/PR CI 取消过期运行，完整构建后上传不再次压缩的 `compat-release` 二进制 artifact，构建证据和失败报告单独上传。实际发布仅由手动 `Compat release` 工作流执行：先建立既存 tag，再输入 `tag=1.0.0-beta1` 与 `confirm=PUBLISH 1.0.0-beta1`。工作流不创建或移动 tag；从 tag 校验版本并完整构建，只有发布 job 获得写权限。beta Release 标记为 prerelease，且 `latest=false`。

`releaseAssets` 复用现有 `build`、`installManifest` 与 Maven publication，精确收集本次输出：

- `build/release/qz_uilib_compat-1.0.0-beta1.jar`
- `build/release/qz-uilib-compat-1.0.0-beta1-maven.zip`
- `build/release/install-manifest.json`

Release 静态正文固定取 tag 中的更新日志，动态证据包含 tag、commit、Actions 链接与实际构建范围。首次发布先创建草稿，资产上传后公开；重跑替换同名资产、保留其他资产，正文不累加证据段。发布工具回归可独立执行：

```sh
python -m unittest discover -s tools -p 'test_*.py'
node --test tools/release.test.mjs
```
