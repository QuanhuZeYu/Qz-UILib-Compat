package club.heiqi.uilib.compat.mc1710.angelica.shared;

import club.heiqi.uilib.host.client.font.compat.*;
import club.heiqi.uilib.host.client.font.context.*;
import club.heiqi.uilib.host.client.gl.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import club.heiqi.uilib.host.client.gl.GlApi;
import java.util.List;
import java.util.IdentityHashMap;
import java.util.Objects;
import club.heiqi.uilib.host.client.font.context.FontRenderContext;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;

/** 按已验证 Angelica ABI 对接有序文字，文字度量由 UILib 提供。
 * 安装状态通过实际 Mixin marker 确认；跨线程或 ABI 失配时报告错误。
 * ReplaySink 负责 Iris pack producer 与有效状态 guard，pack 支持以实际 program/vertex ABI 验证为准。 */
public final class AngelicaBridge implements OptionalFontHostBridge {
    private static AngelicaBridge active;
    private static final Runnable MERGED_TICKET = () -> {};
    private final Thread thread = Thread.currentThread();
    private final ReplaySink sink;
    private final FontHostOrderQueue queue;
    private final HostProgramSnapshot.Capture snapshots;
    private final ExternalReplayBoundary replayBoundary;
    private final Method seal, defer, recording, listMode, record, pause, resume;
    private final Field arena, depth, segments, deferred, segmentOwner, from, to;
    private final Class<?> commandType;
    private int flushDepth;
    private boolean closed, primed;
    private FontReplayRun fontTail;
    private final IdentityHashMap<Object, Boolean> replayed =
            new IdentityHashMap<Object, Boolean>();

    public AngelicaBridge(Class<?> font, ClassLoader loader, GlApi gl, ReplaySink sink, int capacity) {
        this.sink = sink;
        snapshots = new HostProgramSnapshot.Capture(loader, gl);
        queue = new FontHostOrderQueue(capacity);
        try {
            replayBoundary = new ExternalReplayBoundary(DrawState.bind(loader));
            font.getMethod("uilibFontOrderHooksInstalled");
            seal = declared(font, "sealBatchSegment");
            defer = declared(font, "shouldDeferNow");
            arena = field(font, "arenaOwner");
            depth = field(font, "batchDepth");
            segments = field(font, "batchSegments");
            deferred = field(font, "deferredSegments");
            Class<?> segment = Class.forName(font.getName() + "$TextSegment", false, loader);
            segmentOwner = field(segment, "owner");
            from = field(segment, "cmdStart");
            to = field(segment, "cmdEnd");
            Class<?> lists =
                    Class.forName(
                            "com.gtnewhorizons.angelica.glsm.DisplayListManager", false, loader);
            commandType =
                    Class.forName(
                            "com.gtnewhorizons.angelica.glsm.recording.commands.DisplayListCommand",
                            false,
                            loader);
            Class<?> recorder =
                    Class.forName(
                            "com.gtnewhorizons.angelica.glsm.recording.CommandRecorder",
                            false,
                            loader);
            recording = lists.getMethod("isRecording");
            listMode = lists.getMethod("getListMode");
            record = lists.getMethod("recordComplexCommand", commandType);
            pause = lists.getMethod("pauseRecording");
            resume = lists.getMethod("resumeRecording", recorder);
            // 构造时校验辅助方法的精确 descriptor，使 ABI 失配在启用延期路径前暴露。
            Class<?> cmds =
                    java.lang.reflect.Array.newInstance(
                                    Class.forName(font.getName() + "$FontDrawCmd", false, loader),
                                    0)
                            .getClass();
            declared(font, "drawCommands", cmds, int.class, int.class, font);
            declared(
                    font,
                    "emitRangeThroughPipeline",
                    font,
                    cmds,
                    int.class,
                    int.class,
                    int.class,
                    float.class,
                    float.class,
                    float.class);
        } catch (ReflectiveOperationException | LinkageError failure) {
            throw new IllegalStateException(
                    "Angelica字体bridge ABI或order mixin未安装；不能安全开启替代", failure);
        }
        if (active != null) throw new IllegalStateException("Angelica字体bridge只能有一个共享owner");
        active = this;
    }

    private static Method declared(Class<?> type, String name, Class<?>... args)
            throws ReflectiveOperationException {
        Method m = type.getDeclaredMethod(name, args);
        m.setAccessible(true);
        return m;
    }

    private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        Field f = type.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static Object invoke(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Angelica接缝执行失败：" + method, failure);
        }
    }

    private static Object read(Field field, Object owner) {
        try {
            return field.get(owner);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Angelica状态读取失败：" + field, failure);
        }
    }

    private void check() {
        if (closed || Thread.currentThread() != thread)
            throw new IllegalStateException("Angelica bridge必须在活动owner宿主线程使用");
    }

    @Override
    public void submit(PaintPlan plan, FontRenderContext context, AutoCloseable lease) {
        Objects.requireNonNull(lease, "sourceLease");
        boolean transferred = false;
        try {
            check();
            Objects.requireNonNull(plan, "plan");
            Objects.requireNonNull(context, "context");
            if ((Boolean) invoke(recording, null)) {
                clearFontTail();
                int mode = ((Number) invoke(listMode, null)).intValue();
                if (mode != 0x1300 && mode != 0x1301) throw new IllegalStateException("未知显示列表录制模式");
                DisplayListFontCommand logical = new DisplayListFontCommand(this, plan, lease);
                invoke(record, null, logical.proxy(commandType));
                transferred = true;
                if (mode == 0x1301) logical.execute();
                return;
            }
            Object owner = read(arena, null);
            if (!primed) {
                for (Object segment : (List<?>) read(deferred, null))
                    register(segment, read(segmentOwner, segment));
                if (owner != null)
                    for (Object segment : (List<?>) read(segments, owner)) register(segment, owner);
                primed = true;
            }
            if (owner != null) invoke(seal, owner);
            boolean queued =
                    (Boolean) invoke(defer, null)
                            || (owner != null && ((Number) read(depth, owner)).intValue() > 0)
                            || !((List<?>) read(deferred, null)).isEmpty();
            HostProgramSnapshot program = snapshots.capture();
            // #81：延期票据在提交时并入本帧需求，即时 drain 的同一需求由 draw 的 prepare 登记。
            if (queued) sink.registerVisible(plan, context);
            if (queued && FontReplayRun.eligible(plan)) {
                FontReplayRun tail = fontTail;
                if (tail != null && tail.accepts(plan, context, program)) {
                    FontReplayRun.Addition addition = new FontReplayRun.Addition(plan);
                    // 每次原调用各占一个 sequence/容量并持有独立 lease；溢出时保留旧 run。
                    queue.submit(MERGED_TICKET, lease);
                    transferred = true;
                    // submit 完成入队后，同宿主线程下已验证的追加条件仍成立。
                    tail.append(addition);
                } else {
                    FontReplayRun run = new FontReplayRun(sink, plan, context, program);
                    queue.submit(() -> {
                        clearFontTail();
                        run.run();
                    }, lease);
                    transferred = true;
                    fontTail = run;
                }
            } else {
                clearFontTail();
                queue.submit(
                        () -> {
                            HostProgramSnapshot.replay(program, () -> sink.draw(plan, context));
                        },
                        lease);
                transferred = true;
            }
            if (!queued) {
                clearFontTail();
                replayBoundary.run(queue::drainTail);
            }
        } finally {
            if (!transferred) FontHostOrderQueue.release(lease);
        }
    }

    void executeList(PaintPlan plan) {
        check();
        clearFontTail();
        Object recorder = invoke(pause, null);
        try {
            replayBoundary.run(() -> {
                FontRenderContext context = sink.capture();
                sink.draw(plan, context);
            });
        } finally {
            // 非 null recorder 赋予本次暂停的恢复权，finally 归还同一实例；null 时保持原状态，遵守 2.2.28 的 recorder 线程归属。
            if (recorder != null) invoke(resume, null, recorder);
        }
    }

    private void onSealed(Object owner) {
        check();
        List<?> list = (List<?>) read(segments, owner);
        if (!list.isEmpty()) {
            Object segment = list.get(list.size() - 1);
            if (!queue.containsOld(segment)) {
                replayed.remove(segment);
                register(segment, owner);
            }
        }
    }

    private void register(Object segment, Object owner) {
        // old segment 划定队列切点，封闭首 ticket 的合并范围以保留切点两侧的顺序。
        clearFontTail();
        queue.sealed(segment);
    }

    private void beforeOld(Object owner, int start, int end) {
        check();
        clearFontTail();
        if (start == end) return;
        Object match = find((List<?>) read(segments, owner), owner, start, end);
        if (match == null) match = find((List<?>) read(deferred, null), owner, start, end);
        if (match != null) {
            // 同一 segment 的屏障消费一次，保持 UILib 文字的单次提交。
            if (replayed.containsKey(match)) return;
            if (!queue.containsOld(match))
                throw new IllegalStateException("旧font segment未被seal hook登记");
            final Object segment = match;
            replayBoundary.withinHostFlush(() -> queue.beforeOld(segment));
            replayed.put(match, Boolean.TRUE);
        } else replayBoundary.withinHostFlush(queue::beforeUnsealedTail);
    }

    private Object find(List<?> list, Object owner, int start, int end) {
        for (Object segment : list)
            if (read(segmentOwner, segment) == owner
                    && ((Number) read(from, segment)).intValue() == start
                    && ((Number) read(to, segment)).intValue() == end) return segment;
        return null;
    }

    public static void sealed(Object owner) {
        if (active != null) active.onSealed(owner);
    }

    public static void beforeOldReplay(Object owner, int start, int end) {
        if (active != null) active.beforeOld(owner, start, end);
    }

    public static void beginFlush() {
        if (active != null) {
            active.check();
            active.clearFontTail();
            active.flushDepth++;
        }
    }

    public static void endFlush() {
        if (active != null) {
            active.check();
            active.flushDepth--;
            if (active.flushDepth == 0) {
                active.clearFontTail();
                active.replayBoundary.run(active.queue::drainTail);
                active.replayed.clear();
            }
        }
    }

    /** 内部 flush 的 discard 早于尾消费；保留新 tail，由最外层 endFlush 完成消费。 */
    public static void discarded() {
        if (active != null && active.flushDepth == 0) active.abort();
    }

    public static void frameEnded() {
        if (active != null) active.abort();
    }

    private void clearFontTail() {
        // 普通屏障封闭合并候选，已入队 run 仍由所属 ticket 绘制。
        fontTail = null;
    }

    private void cancelFontTail() {
        if (fontTail != null) fontTail.cancel();
        clearFontTail();
    }

    @Override
    public void abort() {
        check();
        cancelFontTail();
        flushDepth = 0;
        replayed.clear();
        queue.close();
    }

    @Override
    public int pending() {
        check();
        return queue.pending();
    }

    @Override
    public void close() {
        if (!closed) {
            check();
            cancelFontTail();
            try {
                queue.close();
            } finally {
                closed = true;
                if (active == this) active = null;
            }
        }
    }
}
