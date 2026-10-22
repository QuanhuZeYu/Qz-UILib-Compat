package club.heiqi.uilib.compat.mc1710.angelica.shared;

import club.heiqi.uilib.host.client.font.FontPerformanceProbe;
import club.heiqi.uilib.host.client.font.compat.OptionalFontHostBridge.ReplaySink;
import club.heiqi.uilib.host.client.font.context.FontRenderContext;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import java.util.Objects;

/** 已知延期范围内相邻同来源文字的逻辑批次；只持plan，不持Prepared、UV或GL scope。
 * 首ticket执行整个run，后续ticket仍各自保有lease及顺序位置，归队列独立释放。 */
final class FontReplayRun implements Runnable {
    private static final int MAX_COMMANDS = 4096;
    private final ReplaySink sink;
    private final FontRenderContext context;
    private final HostProgramSnapshot source;
    private Addition first, last;
    private int commandCount;

    FontReplayRun(ReplaySink sink, PaintPlan plan, FontRenderContext context,
            HostProgramSnapshot source) {
        this.sink = Objects.requireNonNull(sink, "sink");
        this.context = Objects.requireNonNull(context, "context");
        this.source = source;
        if (!eligible(plan)) throw new IllegalArgumentException("只有有界flat TEXT计划可加入font run");
        first = last = new Addition(plan);
        commandCount = first.commandCount;
    }

    static boolean eligible(PaintPlan plan) {
        // #81临时探针：按原有资格判断调用计数，性能报告定位后统一移除。
        if (plan.commands().isEmpty() || plan.commands().size() > MAX_COMMANDS) {
            FontPerformanceProbe.count(FontPerformanceProbe.HOST_INELIGIBLE, 1);
            return false;
        }
        for (PaintCommand command : plan.commands())
            if (command.kind != PaintCommand.Kind.TEXT || command.pendingPaint != null
                    || command.failedPaint != null || command.readyOverlay != null
                    || command.branchSource != null) {
                FontPerformanceProbe.count(FontPerformanceProbe.HOST_INELIGIBLE, 1);
                return false;
            }
        FontPerformanceProbe.count(FontPerformanceProbe.HOST_ELIGIBLE, 1);
        return true;
    }

    boolean accepts(PaintPlan plan, FontRenderContext context, HostProgramSnapshot source) {
        return first != null && eligible(plan)
                && plan.commands().size() <= MAX_COMMANDS - commandCount
                && FontReplayCompatibility.same(this.context, context, this.source, source);
    }

    /** 入队前分配节点，成功转移lease后append只链接已验证节点，不再分配容器。 */
    static final class Addition {
        final PaintPlan plan;
        final int commandCount;
        Addition next;

        Addition(PaintPlan plan) {
            this.plan = plan;
            commandCount = plan.commands().size();
        }
    }

    void append(Addition addition) {
        if (first == null) throw new IllegalStateException("font run已消费或取消");
        if (addition.commandCount > MAX_COMMANDS - commandCount)
            throw new IllegalStateException("font run命令容量超限");
        last.next = addition;
        last = addition;
        commandCount += addition.commandCount;
    }

    @Override
    public void run() {
        // #81临时探针：完整run含sink.draw后代计时，性能报告定位后统一移除。
        long replayToken = FontPerformanceProbe.begin(FontPerformanceProbe.HOST_REPLAY);
        try {
            Addition plans = first;
            if (plans == null) throw new IllegalStateException("font run不能重复回放");
            // 开始即封闭，异常及回放后的追加同样会被拒绝；局部链只活到本次draw退出。
            cancel();
            PaintPlan merged;
            if (plans.next == null) {
                FontPerformanceProbe.count(FontPerformanceProbe.HOST_SINGLE_RUNS, 1);
                FontPerformanceProbe.count(FontPerformanceProbe.HOST_RUN_PLANS, 1);
                FontPerformanceProbe.count(FontPerformanceProbe.HOST_RUN_COMMANDS, plans.commandCount);
                merged = plans.plan;
            } else {
                FontPerformanceProbe.count(FontPerformanceProbe.HOST_MERGED_RUNS, 1);
                // #81临时探针：仅框定计划拼接构建，不包含sink.draw及其后代。
                long mergeToken = FontPerformanceProbe.begin(FontPerformanceProbe.HOST_MERGE);
                try {
                    PaintPlanBuilder builder = new PaintPlanBuilder();
                    for (Addition part = plans; part != null; part = part.next) {
                        FontPerformanceProbe.count(FontPerformanceProbe.HOST_RUN_PLANS, 1);
                        FontPerformanceProbe.count(FontPerformanceProbe.HOST_RUN_COMMANDS, part.commandCount);
                        for (PaintCommand command : part.plan.commands()) builder.add(command);
                    }
                    merged = builder.build();
                } finally {
                    FontPerformanceProbe.end(FontPerformanceProbe.HOST_MERGE, mergeToken);
                }
            }
            HostProgramSnapshot.replay(source, () -> sink.draw(merged, context));
        } finally {
            FontPerformanceProbe.end(FontPerformanceProbe.HOST_REPLAY, replayToken);
        }
    }

    /** 只丢弃逻辑载荷；source lease仍唯一归原队列ticket，不在run里释放。 */
    void cancel() {
        first = last = null;
        commandCount = 0;
    }
}
