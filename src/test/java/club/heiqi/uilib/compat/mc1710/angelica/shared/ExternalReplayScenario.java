package club.heiqi.uilib.compat.mc1710.angelica.shared;

import club.heiqi.uilib.host.client.font.compat.FontHostOrderQueue;

/** 以替代 Angelica 状态变更、GL 与 Prepared 端点执行生产边界和顺序队列。
 * 复现 #74 的 viewport → host flush → queue replay → 第二个 Prepared 调用链，验证几何顺序、单 Prepared 与租约释放。 */
public final class ExternalReplayScenario {
    static final class Host implements DrawState {
        int suspended, preparations, preflushes, geometry, draws;
        boolean pending, replayText;
        Runnable flushed = () -> {};
        public void stabilizeHost() {
            if (suspended != 0) return;
            preflushes++;
            suspended++;
            try {
                if (pending || replayText) {
                    if (pending) geometry++;
                    pending = false; replayText = false; flushed.run();
                }
            }
            finally { suspended--; }
        }
        public void suspendHost() { suspended++; }
        public void resumeHost() { suspended--; }
        public void beforeDraw(int mode) {}
        public void refreshUniforms(int program) {}
        public int vertexFlags() { return 0; }
        void draw(boolean fail) {
            if (preparations != 0) throw new IllegalStateException("共享atlas只允许即时短Prepared");
            preparations++;
            try {
                // viewport 设置与 producer/GL 恢复均经过状态屏障，覆盖入口和清理阶段的回调。
                stabilizeHost();
                if (pending) throw new AssertionError("文字早于木牌几何");
                draws++;
                if (fail) throw new IllegalArgumentException("draw failure");
            } finally { stabilizeHost(); preparations--; }
        }
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) {
        Host host = new Host();
        ExternalReplayBoundary boundary = new ExternalReplayBoundary(host);
        FontHostOrderQueue queue = new FontHostOrderQueue(4);
        int[] leases = {0};
        host.flushed = () -> boundary.run(queue::drainTail);
        queue.submit(() -> host.draw(false), () -> leases[0]++);
        host.pending = true;
        // 显示列表即时回放的前置屏障先完成旧几何与旧 ticket。
        boundary.run(() -> host.draw(false));
        require(host.draws == 2 && host.geometry == 1 && leases[0] == 1 && queue.pending() == 0,
                "列表回放没有先完成几何与旧文字，或重复消费/吞票");
        int previous = host.preflushes;
        queue.submit(() -> host.draw(false), () -> leases[0]++);
        host.pending = true;
        // 对齐真实宿主 flushDeferredText：回调进入时 Guard 已由宿主持有。
        host.stabilizeHost();
        require(host.draws == 3 && leases[0] == 2 && host.preflushes == previous + 1,
                "宿主屏障回调递归preflush或没有释放ticket");
        host.suspended++;
        boundary.run(() -> host.draw(false));
        require(host.suspended == 1, "借用宿主guard时错误归还了宿主所有权");
        host.suspended--;
        queue.submit(() -> host.draw(true), () -> leases[0]++);
        queue.submit(() -> host.draw(false), () -> leases[0]++);
        try { boundary.run(queue::drainTail); throw new AssertionError("异常被吞掉"); }
        catch (IllegalArgumentException expected) { require("draw failure".equals(expected.getMessage()), "异常被替换"); }
        require(host.suspended == 0 && host.preparations == 0 && leases[0] == 4 && queue.pending() == 0,
                "异常退出没有恢复guard/Prepared或归还全部source lease");
        // beforeOld 与未 seal 尾段共用同一消费边界。
        Object old = new Object();
        queue.submit(() -> host.draw(false), () -> leases[0]++); queue.sealed(old);
        boundary.run(() -> queue.beforeOld(old));
        queue.submit(() -> host.draw(false), () -> leases[0]++);
        boundary.run(queue::beforeUnsealedTail);
        require(leases[0] == 6 && host.suspended == 0, "旧段屏障丢票或泄漏guard");
        Object iterating = new Object();
        queue.submit(() -> host.draw(false), () -> leases[0]++); queue.sealed(iterating);
        host.replayText = true;
        host.flushed = () -> boundary.withinHostFlush(() -> queue.beforeOld(iterating));
        // 宿主遍历 segment 时，递归 preflush 会先在内层消费身份并使外层丢失旧段；此处验证原遍历内消费。
        boundary.withinHostFlush(() -> queue.beforeOld(iterating));
        require(leases[0] == 7 && queue.pending() == 0 && host.replayText, "宿主旧段迭代不应再次启动宿主flush");
        host.replayText = false;
        System.out.println("外部回放回归通过：几何先行、单Prepared、无队列重入、宿主guard所有权、异常与租约释放");
    }
}
