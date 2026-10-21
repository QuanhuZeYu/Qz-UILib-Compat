package club.heiqi.uilib.compat.mc1710.angelica.shared;

import java.lang.reflect.InvocationTargetException;

/** 外部文字的同步消费边界；宿主批次必须在共享Prepared之前完成。
 * #74：viewport等状态切换会flush TESR并回调文字队列，不能在Prepared存活期间首次稳定宿主。
 * 稳定动作先于队列取票；宿主flush内同步回放借用外层边界，不再次preflush。
 * suspend只覆盖已稳定后的短回放及其全部GL/producer恢复，不延迟几何，也不放宽单Prepared合同。 */
final class ExternalReplayBoundary {
    private final DrawState state;
    private boolean active;
    ExternalReplayBoundary(DrawState state) { this.state = state; }

    /** 已处于旧字体segment遍历中，不能再flush同一集合；仅保护插入其间的短回放。 */
    void withinHostFlush(Runnable replay) { run(replay, false); }

    void run(Runnable replay) { run(replay, true); }

    private void run(Runnable replay, boolean stabilize) {
        if (active) { replay.run(); return; }
        active = true;
        try {
            if (stabilize) invoke(0);
            invoke(1);
            try (Guard ignored = new Guard()) { replay.run(); }
        } finally { active = false; }
    }

    private final class Guard implements AutoCloseable {
        public void close() { invoke(2); }
    }

    private void invoke(int operation) {
        try {
            if (operation == 0) state.stabilizeHost();
            else if (operation == 1) state.suspendHost();
            else state.resumeHost();
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException("Angelica外部回放屏障失败", cause);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Angelica外部回放屏障ABI失败", failure);
        }
    }
}
