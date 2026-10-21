package club.heiqi.uilib.compat.mc1710.angelica.shared;

import java.lang.reflect.*;
import club.heiqi.uilib.host.client.font.compat.OptionalFontHostBridge;
import club.heiqi.uilib.host.client.font.context.*;
import club.heiqi.uilib.ui.scene.paint.*;

/** 直接执行生产submit/executeList/proxy；只替换外部录制器端点，不需要GL上下文。 */
public final class AngelicaReplayScenario {
    public interface Command { void execute(); void delete(); }
    public static final class Lists {
        public static Object recorder, resumed;
        public static int resumes, mode;
        public static Command command;
        public static boolean recording() { return true; }
        public static int mode() { return mode; }
        public static void record(Command value) { command = value; }
        public static Object pause() { return recorder; }
        public static void resume(Object value) {
            if (value == null) throw new IllegalStateException("Cannot resume another thread display list compilation");
            resumed = value; resumes++;
        }
    }
    private static void set(Object target, String name, Object value) throws Exception {
        Field field = AngelicaBridge.class.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    private static void require(boolean result, String message) { if (!result) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Field singleton = unsafeType.getDeclaredField("theUnsafe"); singleton.setAccessible(true);
        Object unsafe = singleton.get(null);
        AngelicaBridge bridge = (AngelicaBridge) unsafeType.getMethod("allocateInstance", Class.class).invoke(unsafe, AngelicaBridge.class);
        int[] captures = {0}, draws = {0}, leases = {0}; boolean[] fail = {false};
        FontRenderContext[] last = {null};
        set(bridge, "thread", Thread.currentThread());
        set(bridge, "sink", new OptionalFontHostBridge.ReplaySink() {
            public FontRenderContext capture() {
                float[] matrix = new float[16]; matrix[0] = matrix[5] = matrix[10] = matrix[15] = 1; matrix[12] = ++captures[0];
                return new FontRenderContext(new FontProjection(matrix, matrix, new int[] {0,0,800,600}),
                        0, 600, false, 0x0203, false, false, new int[] {0,0,800,600},
                        FontRenderContext.MatrixState.ui(matrix,matrix), new Object(), 1, 0);
            }
            public void registerVisible(PaintPlan plan, FontRenderContext context) { require(context != last[0], "回放重用了编译期矩阵"); }
            public void draw(PaintPlan plan, FontRenderContext context) {
                last[0] = context; draws[0]++; if (fail[0]) throw new IllegalStateException("draw failure");
            }
        });
        for (String[] endpoint : new String[][] {{"pause","pause"},{"recording","recording"},{"listMode","mode"}})
            set(bridge, endpoint[0], Lists.class.getMethod(endpoint[1]));
        set(bridge, "resume", Lists.class.getMethod("resume", Object.class));
        set(bridge, "record", Lists.class.getMethod("record", Command.class)); set(bridge, "commandType", Command.class);
        PaintPlan plan = new PaintPlanBuilder().build();
        Lists.mode = 0x1300; Lists.recorder = null;
        bridge.submit(plan, last[0] == null ? new OptionalFontHostBridge.ReplaySink() {
            public void draw(PaintPlan p, FontRenderContext c) {} public void registerVisible(PaintPlan p, FontRenderContext c) {}
            public FontRenderContext capture() { float[] m = new float[16]; m[0]=m[5]=m[10]=m[15]=1;
                return new FontRenderContext(new FontProjection(m,m,new int[]{0,0,800,600}),0,600,false,0x0203,false,false,new int[]{0,0,800,600},FontRenderContext.MatrixState.ui(m,m),new Object(),1,0); }
        }.capture() : last[0], () -> leases[0]++);
        require(draws[0] == 0 && leases[0] == 0, "COMPILE不应执行或提前释放");
        Lists.command.execute(); Lists.command.execute();
        require(draws[0] == 2 && captures[0] == 2 && Lists.resumes == 0, "普通回放不得resume(null)，且每次重捕获当前矩阵");
        Object recorder = new Object(); Lists.recorder = recorder; fail[0] = true;
        try { Lists.command.execute(); throw new AssertionError("异常未传播"); } catch (IllegalStateException expected) {}
        require(Lists.resumes == 1 && Lists.resumed == recorder, "异常路径没有归还取得的recorder");
        Lists.command.delete(); Lists.command.delete(); require(leases[0] == 1, "delete必须仅释放一次源租约");
        try { Lists.command.execute(); throw new AssertionError("delete后仍执行"); } catch (IllegalStateException expected) {}
        fail[0] = false; Lists.mode = 0x1301; int before = draws[0];
        bridge.submit(plan, last[0], () -> leases[0]++);
        require(draws[0] == before + 1 && Lists.resumes == 2, "COMPILE_AND_EXECUTE应立即执行一次并恢复所属录制器");
        Lists.command.delete(); require(leases[0] == 2, "第二次录制租约未释放");
        System.out.println("ANGELICA_REPLAY_PASS compile/compileAndExecute currentMatrices nullRecorderNoResume ownedRecorderExceptionRestore deleteLeaseExactlyOnce");
    }
}
