package club.heiqi.uilib.compat.mc1710.angelica.shared;

import club.heiqi.uilib.compat.mc1710.AngelicaAbi;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** 按Angelica ABI选择版本适配；版本专属绑定留在各版本目录，这里只做选择与共享入口的一次性绑定缓存。 */
public final class AngelicaVersions {
    private static final AngelicaVersion[] ALL = {
        new club.heiqi.uilib.compat.mc1710.angelica.v2210.AngelicaVersion2210(),
        new club.heiqi.uilib.compat.mc1710.angelica.v2228.AngelicaVersion2228(),
        new club.heiqi.uilib.compat.mc1710.angelica.v2243.AngelicaVersion2243()
    };
    private static final Map<ClassLoader, GlsmEntries> ENTRIES =
            Collections.synchronizedMap(new WeakHashMap<ClassLoader, GlsmEntries>());
    private AngelicaVersions() {}

    public static AngelicaVersion select(ClassLoader loader) {
        String abi = AngelicaAbi.select(loader);
        if (abi == null) return null;
        for (AngelicaVersion version : ALL) if (version.id().equals(abi)) return version;
        throw new IllegalStateException("Angelica ABI存在但无对应适配：" + abi);
    }

    /** provider/producer/snapshot共用同一份绑定；Angelica缺席返回null。 */
    public static GlsmEntries entries(ClassLoader loader) {
        synchronized (ENTRIES) {
            GlsmEntries cached = ENTRIES.get(loader);
            if (cached != null) return cached;
        }
        AngelicaVersion version = select(loader);
        if (version == null) return null;
        GlsmEntries entries = version.glsmEntries(loader);
        synchronized (ENTRIES) { ENTRIES.put(loader, entries); }
        return entries;
    }
}
