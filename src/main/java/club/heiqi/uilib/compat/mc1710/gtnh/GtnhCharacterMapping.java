package club.heiqi.uilib.compat.mc1710.gtnh;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.IntUnaryOperator;
import club.heiqi.uilib.ui.config.ConfigCodecs;
import club.heiqi.uilib.ui.config.ConfigDocument;
import club.heiqi.uilib.ui.config.ConfigKey;
import club.heiqi.uilib.ui.config.ConfigSchema;

/** 将 GTNH 私用符号映射为显示字符；业务原文保持原值，字体候选与引擎由 UILib 管理。 */
public final class GtnhCharacterMapping {
    private static final ConfigKey<Boolean> ENABLED =
            new ConfigKey<>("gtnh.mapPrivateCharacters", ConfigCodecs.BOOLEAN, true)
                    .withComment("将GTNH私用符号显示为登记Unicode字符或已选相似符号；修改后F3T或重启生效。");
    private static final ConfigSchema SCHEMA = ConfigSchema.of(ENABLED);
    private static final IntUnaryOperator OFF = cp -> cp;
    private static final IntUnaryOperator ON = GtnhCharacterMapping::mapped;
    private static volatile IntUnaryOperator current = ON;
    private static Thread owner;
    private static Path path;

    private GtnhCharacterMapping() {}

    public static void initialize(Path file) throws IOException {
        if (owner != null) throw new IllegalStateException("兼容配置重复初始化");
        owner = Thread.currentThread();
        path = Objects.requireNonNull(file, "file");
        reload();
    }

    /** 加载器或资源重载 owner 读取配置，校验成功后发布稳定 ON/OFF 快照；失败时保留上次快照。 */
    public static void reload() throws IOException {
        if (Thread.currentThread() != owner) throw new IllegalStateException("兼容配置仅限owner线程加载");
        ConfigDocument document;
        if (Files.exists(path)) document = ConfigDocument.read(SCHEMA, path);
        else {
            document = ConfigDocument.defaults(SCHEMA);
            document.write(path);
        }
        current = document.get(ENABLED) ? ON : OFF;
    }

    public static IntUnaryOperator snapshot() { return current; }

    /* 原二十项对应 GregTech CustomGlyphs（629e4105）的 registerCustomGlyph 声明，
     * 由 GTNHLib GlyphReplacements（d6dbd833）登记；ALEPH 对应希伯来字母 U+05D0，U+2135 为数学符号。
     * 其余十三项采用 issue #53 中用户从对比图选定的相似显示替代，独立于上游登记关系。
     * GALAXY 用形似的 U+058D；问号用 U+FE56 的普通标点语义与外形，保持字号和基线。
     * 硫黄使用普通炼金硫黄 U+1F70D；此表记录字符对应关系与显示替代的来源。
     */
    private static int mapped(int cp) {
        switch (cp) {
            case 0xE000: return 0x1F701;
            case 0xE001: return 0x1F703;
            case 0xE002: return 0x1F702;
            case 0xE003: return 0x1F704;
            case 0xE004: return 0x22C0;
            case 0xE005: return 0x22C1;
            case 0xE006: return 0x2742;
            case 0xE007: return 0x26CF;
            case 0xE008: return 0x21F2;
            case 0xE009: return 0x21F1;
            case 0xE00A: return 0x26A1;
            case 0xE00B: return 0x2738;
            case 0xE00C: return 0x058D;
            case 0xE00D: return 0x2728;
            case 0xE00E: return 0x1F9F2;
            case 0xE00F: return 0x2205;
            case 0xE010: return 0x2070;
            case 0xE011: return 0x00B9;
            case 0xE012: return 0x00B2;
            case 0xE013: return 0x00B3;
            case 0xE014: return 0x2074;
            case 0xE015: return 0x2075;
            case 0xE016: return 0x2076;
            case 0xE017: return 0x2077;
            case 0xE018: return 0x2078;
            case 0xE019: return 0x2079;
            case 0xE01A: return 0x2080;
            case 0xE01B: return 0x05D0;
            case 0xE01C: return 0x03C9;
            case 0xE01D: return 0x300C;
            case 0xE01E: return 0x1F70D;
            case 0xE01F: return 0x2A02;
            case 0xE020: return 0xFE56;
            default: return cp;
        }
    }
}
