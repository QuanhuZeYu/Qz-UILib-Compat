package club.heiqi.uilib.compat.mc1710.angelica.shared;

import club.heiqi.uilib.host.client.font.compat.*;
import club.heiqi.uilib.host.client.font.context.*;
import club.heiqi.uilib.host.client.gl.*;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;

/** 在宿主 complex opcode 位置回放；编译保存逻辑 plan 与 source lease，回放重新捕获状态并申请字形，delete 释放 lease。 */
final class DisplayListFontCommand implements InvocationHandler {
    private AngelicaBridge host;
    private PaintPlan plan;
    private AutoCloseable lease;

    DisplayListFontCommand(AngelicaBridge host, PaintPlan plan, AutoCloseable lease) {
        this.host = host;
        this.plan = plan;
        this.lease = lease;
    }

    Object proxy(Class<?> command) {
        return Proxy.newProxyInstance(command.getClassLoader(), new Class<?>[] {command}, this);
    }

    void execute() {
        if (lease == null) throw new IllegalStateException("已delete的font列表command");
        host.executeList(plan);
    }

    void delete() {
        if (lease != null) {
            AutoCloseable source = lease;
            lease = null;
            plan = null;
            host = null;
            FontHostOrderQueue.release(source);
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        switch (method.getName()) {
            case "execute":
                execute();
                return null;
            case "delete":
                delete();
                return null;
            case "toString":
                return "UILib logical font display-list command";
            case "hashCode":
                return System.identityHashCode(proxy);
            case "equals":
                return proxy == args[0];
            default:
                throw new IllegalStateException("Angelica DisplayListCommand ABI不匹配：" + method);
        }
    }
}
