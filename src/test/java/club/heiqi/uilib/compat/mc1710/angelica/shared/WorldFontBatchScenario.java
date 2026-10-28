package club.heiqi.uilib.compat.mc1710.angelica.shared;

import club.heiqi.uilib.host.client.font.compat.FontHostOrderQueue;
import club.heiqi.uilib.host.client.font.compat.OptionalFontHostBridge;
import club.heiqi.uilib.host.client.font.context.*;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.paint.*;
import club.heiqi.uilib.ui.scene.text.*;
import java.lang.reflect.*;
import java.util.*;

/** 以替代宿主端点执行生产 Bridge/有序队列，统计逻辑批次与票据顺序；实际 GL draw 数由 GL 场景验证。 */
final class WorldFontBatchScenario {
    public static final class Endpoints {
        public static Object arena;
        public static final List<Object> deferred = new ArrayList<>();
        public static boolean defer = true;
        public static boolean recording() { return false; }
        public static boolean defer() { return defer; }
    }
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static void set(Object owner, String name, Object value) throws Exception {
        Field field = AngelicaBridge.class.getDeclaredField(name);
        field.setAccessible(true); field.set(owner, value);
    }
    private static float[] identity() {
        float[] value = new float[16]; value[0] = value[5] = value[10] = value[15] = 1; return value;
    }
    private static FontRenderContext context(Object owner, int translation, int pass, long revision) {
        float[] p = identity(), mv = identity(); mv[12] = translation;
        FontRenderContext.StencilFace stencil = new FontRenderContext.StencilFace(0x0207,0,-1,-1,0x1E00,0x1E00,0x1E00);
        FontRenderContext.RasterState raster = new FontRenderContext.RasterState(
                true,0x0302,0x0303,1,0x0303,0x8006,0x8006,new float[4],false,stencil,stencil,
                new boolean[] {true,true,true,true},new float[] {0,1},false,0x0405,0x0901,false,0,0);
        return new FontRenderContext(new FontProjection(p,mv,new int[] {0,0,800,600}),
                0,600,true,0x0203,false,false,new int[] {0,0,800,600},
                FontRenderContext.MatrixState.ui(p,mv),owner,revision,pass,raster);
    }
    private static final class Fixture implements AutoCloseable {
        final AngelicaBridge bridge;
        final FontHostOrderQueue queue;
        final ExternalReplayScenario.Host host = new ExternalReplayScenario.Host();
        final ExternalReplayBoundary boundary = new ExternalReplayBoundary(host);
        final List<String> order = new ArrayList<>();
        final List<Integer> sizes = new ArrayList<>();
        final Map<PaintCommand,Integer> markers = new IdentityHashMap<>();
        final Map<Integer,int[]> releases = new LinkedHashMap<>();
        RuntimeException failure;
        Fixture(int capacity) throws Exception {
            Class<?> type = Class.forName("sun.misc.Unsafe");
            Field field = type.getDeclaredField("theUnsafe"); field.setAccessible(true);
            bridge = (AngelicaBridge) type.getMethod("allocateInstance",Class.class)
                    .invoke(field.get(null),AngelicaBridge.class);
            queue = new FontHostOrderQueue(capacity);
            Endpoints.defer = true; Endpoints.arena = null; Endpoints.deferred.clear();
            set(bridge,"thread",Thread.currentThread()); set(bridge,"queue",queue);
            set(bridge,"replayed",new IdentityHashMap<Object,Boolean>());
            set(bridge,"replayBoundary",boundary); set(bridge,"primed",true);
            set(bridge,"arena",Endpoints.class.getField("arena"));
            set(bridge,"deferred",Endpoints.class.getField("deferred"));
            set(bridge,"defer",Endpoints.class.getMethod("defer"));
            set(bridge,"recording",Endpoints.class.getMethod("recording"));
            set(bridge,"snapshots",new HostProgramSnapshot.Capture(getClass().getClassLoader(),null));
            set(bridge,"sink",new OptionalFontHostBridge.ReplaySink() {
                public FontRenderContext capture() { throw new AssertionError("延期文字不应重捕矩阵"); }
                public void registerVisible(PaintPlan plan, FontRenderContext context) {}
                public void draw(PaintPlan plan, FontRenderContext context) {
                    sizes.add(plan.commands().size());
                    for (PaintCommand command : plan.commands()) {
                        int marker = markers.get(command);
                        require(releases.get(marker)[0] == 0,"合并绘制前来源已释放: " + marker);
                        order.add(Integer.toString(marker));
                    }
                    host.draw(false);
                    if (failure != null) throw failure;
                }
            });
            Field active = AngelicaBridge.class.getDeclaredField("active");
            active.setAccessible(true); active.set(null,bridge);
        }
        void submit(int marker, FontRenderContext context) {
            TextLayout text = new TextLayout(1,1,new TextStyle("batch",12,-1),
                    Collections.singletonList(new TextLayout.Line("x",0,0)));
            PaintPlan plan = new PaintPlanBuilder().add(PaintCommand.text(new LayoutBox(0,marker,1,1),null,text)).build();
            markers.put(plan.commands().get(0),marker);
            int[] count = {0}; releases.put(marker,count);
            bridge.submit(plan,context,() -> count[0]++);
        }
        void seal(Object old) throws Exception {
            Method method = AngelicaBridge.class.getDeclaredMethod("register",Object.class,Object.class);
            method.setAccessible(true); method.invoke(bridge,old,null);
        }
        void flush() { AngelicaBridge.beginFlush(); AngelicaBridge.endFlush(); }
        void released() {
            for (Map.Entry<Integer,int[]> entry : releases.entrySet())
                require(entry.getValue()[0] == 1,"来源未且仅释放一次: " + entry.getKey());
            require(queue.pending() == 0 && host.suspended == 0 && host.preparations == 0,"批次泄漏票据或绘制作用域");
        }
        public void close() { bridge.close(); }
    }
    static void run() throws Exception {
        Object owner = new Object();
        FontRenderContext a = context(owner,0,1,1), equal = context(owner,0,1,1), b = context(owner,1,1,1);
        require(FontReplayCompatibility.same(a,equal,null,null),"相同值的来源未兼容");
        require(!FontReplayCompatibility.same(a,b,null,null),"异矩阵来源被合并");
        require(!FontReplayCompatibility.same(a,context(new Object(),0,1,1),null,null),"跨context合批");
        require(!FontReplayCompatibility.same(a,context(owner,0,2,1),null,null),"跨pass合批");
        require(!FontReplayCompatibility.same(a,context(owner,0,1,2),null,null),"跨资源代合批");
        pipelineBoundary(a,equal);
        try (Fixture f = new Fixture(4)) {
            f.submit(1,a); f.submit(2,equal); f.submit(3,a);
            require(f.bridge.pending() == 3 && f.sizes.isEmpty(),"合批改变票据容量或提前绘制");
            f.flush();
            require(f.sizes.equals(Arrays.asList(3)) && f.order.equals(Arrays.asList("1","2","3")),"连续同源文字未一次保序回放");
            f.released();
            f.submit(4,a); f.submit(5,b); f.submit(6,equal); f.flush();
            require(f.sizes.equals(Arrays.asList(3,1,1,1))
                    && f.order.equals(Arrays.asList("1","2","3","4","5","6")),"A/B/A来源被重排或消费后继续追加");
            f.released();
        }
        try (Fixture f = new Fixture(4)) {
            f.submit(7,a); Object old = new Object(); f.seal(old);
            f.submit(8,equal); f.submit(9,a);
            f.boundary.withinHostFlush(() -> f.queue.beforeOld(old));
            require(f.order.equals(Arrays.asList("7")) && f.releases.get(8)[0] == 0,"旧绘制切点提前消费了后段来源");
            f.order.add("old"); f.flush();
            require(f.order.equals(Arrays.asList("7","old","8","9")) && f.sizes.equals(Arrays.asList(1,2)),"合批跨越宿主几何切点");
            f.released();
        }
        try (Fixture f = new Fixture(2)) {
            f.submit(10,a); f.submit(11,equal);
            try { f.submit(12,a); throw new AssertionError("合批规避队列容量"); }
            catch (IllegalStateException error) { require(error.getMessage().contains("有界queue"),"溢出诊断丢失"); }
            require(f.bridge.pending() == 2 && f.releases.get(12)[0] == 1,"溢出来源未立即归还");
            f.flush();
            require(f.order.equals(Arrays.asList("10","11")) && f.sizes.equals(Arrays.asList(2)),"未入队payload污染了旧批次");
            f.released();
        }
        try (Fixture f = new Fixture(4)) {
            f.submit(13,a); f.submit(14,equal);
            RuntimeException sentinel = f.failure = new RuntimeException("批次绘制异常标记");
            try { f.flush(); throw new AssertionError("异常被吞掉"); }
            catch (RuntimeException error) { require(error == sentinel,"批次异常被替换"); }
            f.released();
        }
        try (Fixture f = new Fixture(4)) {
            f.submit(15,a); f.submit(16,equal); AngelicaBridge.frameEnded();
            require(f.sizes.isEmpty(),"帧结束将旧来源补画进新pass"); f.released();
            f.submit(17,a); AngelicaBridge.discarded(); f.released();
            f.submit(18,a); f.close(); f.close(); f.released();
        }
        try (Fixture f = new Fixture(4)) {
            Endpoints.defer = false;
            f.submit(19,a); f.submit(20,equal);
            require(f.sizes.equals(Arrays.asList(1,1)) && f.bridge.pending() == 0,"普通即时调用被暗中延后");
            f.released();
        }
        System.out.println("WORLD_FONT_BATCH_PASS sameSource=3to1 orderedABA=true oldBarrier=true overflowAtomic=true leasesExactlyOnce=true failureFrameClose=true immediate=true shaderExecuted=false");
    }
    /** 检查来源值的合批边界；双发行 Jar 的快照场景另行验证 Iris 换代拒绝。 */
    private static void pipelineBoundary(FontRenderContext a, FontRenderContext b) throws Exception {
        Constructor<HostProgramSnapshot> ctor = HostProgramSnapshot.class.getDeclaredConstructor(
                HostProgramSnapshot.Capture.class,Object.class,Object.class,int.class,boolean.class,
                int.class,int.class,float[].class,SourceExtendedAttributes.class);
        ctor.setAccessible(true);
        Object pipeline = new Object(), phase = new Object();
        HostProgramSnapshot s = ctor.newInstance(null,pipeline,phase,1,true,2,3,identity(),null);
        HostProgramSnapshot equal = ctor.newInstance(null,pipeline,phase,1,true,2,3,identity(),null);
        HostProgramSnapshot pack = ctor.newInstance(null,pipeline,phase,2,true,2,3,identity(),null);
        HostProgramSnapshot replaced = ctor.newInstance(null,new Object(),phase,1,true,2,3,identity(),null);
        require(FontReplayCompatibility.same(a,b,s,equal),"相同来源值未兼容");
        require(!FontReplayCompatibility.same(a,b,s,pack),"跨资源包代际合批");
        require(!FontReplayCompatibility.same(a,b,s,replaced),"跨pipeline合批");
        require(!FontReplayCompatibility.same(a,b,s,null),"跨shader有无来源合批");
    }
}
