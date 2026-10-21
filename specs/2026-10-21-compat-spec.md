# UILib 可选 Mod 兼容规格

## 目标与边界

Qz-UILib-Compat 是独立 MIT Forge 1.7.10 Mod，modId 为 `qz_uilib_compat`，输出 Java8 字节码。单向消费 UILib 正式 Maven 产物与明确发布的 Minecraft development 接口 Jar，不读取相邻生产源码。主仓只持有原版宿主及中性 SPI；Angelica/Iris、lwjgl3ify/RFB 和 AE2 接入归本仓。按 Minecraft、Mod 和必要 ABI 分包，一个发行 Jar 内按 class 资源与方法 descriptor 选择适配；目标缺席时不加载其实现或应用其 Mixin。

## 职责与行为

UILib scene、信号、PaintPlan、字体、资源与 GPU backend 继续拥有呈现权威。兼容层仅提供有效宿主状态、外部 program/vertex ABI、原生事件事实及提交屏障，不复制渲染体系、不删除功能。中性 SPI 缺席时使用原版即时路径；已检测到的外部 ABI 不匹配须明确诊断。

Angelica 2.2.10 的 pipeline helper 返回 boolean、ctx 包可见；2.2.28 返回 void、ctx public，分别选择精确 Mixin。显示列表捕获逻辑 PaintPlan 与其源租约，回放时读取当前有效矩阵并申请当前字形资源；COMPILE_AND_EXECUTE 同时执行一次。暂停只有取得非 null recorder 才拥有恢复权，finally 归还该 recorder；普通回放不得 resume(null)。

AE2 monitor 的显示列表随字体替代开关及既有 FontTextService 排版代际（同来源候选变更也换代）变化重新生成，防止旧原版字符和 UILib 逻辑命令混存。有 Angelica 时保留逻辑列表；无 Angelica 且替换开启时，仅跳过 glNewList/glEndList 并逐帧执行原 AE2 绘制体，避免把 VAO、uniform 和未来字形录入原生列表；关闭后重建原生列表。不固化 GPU 句柄或镜头矩阵。保持存储、转换、吞吐监控原有业务、数量更新与六向放置。对应缺陷：QuanhuZeYu/Qz-UILib#69。

## 验证与安装

完整 build 检查 Java8 输出及产物边界；用两个 Prism 实际 Angelica Jar 校验 descriptor、Mixin 选择与显示列表暂停所有权，覆盖缺席路径。安装输出 `build/install-manifest.json`，schemaVersion=1、host=legacy，列出精确文件和 Maven 依赖身份及 SHA256。安装必须显式选择目标实例；实机验收覆盖六向监控、移动/旋转镜头、数量变化、冷字形、开关来回切换与重进服；编译/ABI 检查不外推为实机成功。
