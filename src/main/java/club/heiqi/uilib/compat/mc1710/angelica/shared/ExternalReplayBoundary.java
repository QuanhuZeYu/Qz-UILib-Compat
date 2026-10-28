package club.heiqi.uilib.compat.mc1710.angelica.shared;

import java.lang.reflect.InvocationTargetException;

/** 外部文字的同步消费边界，宿主批次先于共享 Prepared 完成。
 * #74：viewport 等状态切换会 flush TESR 并回调文字队列，宿主稳定先于取票和 Prepared 创建。
 * 宿主 flush 内的同步回放复用外层已稳定边界，保留几何的同步提交顺序。
 * 稳定后由 suspend 覆盖短回放及全部 GL/producer 恢复，维持单 Prepared 生命周期。 */
final class ExternalReplayBoundary {
    private final DrawState state;
    private boolean active;
    ExternalReplayBoundary(DrawState state) { this.state = state; }

    /** 复用正在遍历旧字体 segment 的宿主边界，以 suspend 保护插入的短回放并保持集合遍历稳定。 */
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
