package club.heiqi.uilib.compat.mc1710.angelica.shared;

import club.heiqi.uilib.host.client.font.compat.OptionalFontHostBridge.ReplaySink;
import club.heiqi.uilib.host.client.font.context.FontRenderContext;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import java.util.Objects;

/** 将已知延期范围内的相邻同来源文字组成逻辑批次，保存 plan、context 与来源快照。
 * Prepared、UV 和 GL scope 在回放时取得；首 ticket 执行整个 run，各 ticket 保留顺序位置与 lease，lease 由队列独立释放。 */
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
        if (plan.commands().isEmpty() || plan.commands().size() > MAX_COMMANDS) {
            return false;
        }
        for (PaintCommand command : plan.commands())
            if (command.kind != PaintCommand.Kind.TEXT || command.pendingPaint != null
                    || command.failedPaint != null || command.readyOverlay != null
                    || command.branchSource != null) {
                return false;
            }
        return true;
    }

    boolean accepts(PaintPlan plan, FontRenderContext context, HostProgramSnapshot source) {
        return first != null && eligible(plan)
                && plan.commands().size() <= MAX_COMMANDS - commandCount
                && FontReplayCompatibility.same(this.context, context, this.source, source);
    }

    /** 入队前分配节点，lease 转移成功后 append 直接链接已验证节点。 */
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
        Addition plans = first;
        if (plans == null) throw new IllegalStateException("font run不能重复回放");
        // 回放开始即封闭追加入口，异常后也保持封闭；局部计划链随本次 draw 退出释放。
        cancel();
        PaintPlan merged;
        if (plans.next == null) {
            merged = plans.plan;
        } else {
            PaintPlanBuilder builder = new PaintPlanBuilder();
            for (Addition part = plans; part != null; part = part.next) {
                for (PaintCommand command : part.plan.commands()) builder.add(command);
            }
            merged = builder.build();
        }
        HostProgramSnapshot.replay(source, () -> sink.draw(merged, context));
    }

    /** 丢弃逻辑载荷，source lease 继续由原队列 ticket 唯一持有并释放。 */
    void cancel() {
        first = last = null;
        commandCount = 0;
    }
}
