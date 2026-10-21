package club.heiqi.uilib.compat.mc1710.gtnh;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntUnaryOperator;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.host.client.extension.MinecraftHostExtension;
import club.heiqi.uilib.host.client.extension.MinecraftHostExtensions;
import club.heiqi.uilib.host.client.font.vanilla.VanillaTextService;
import club.heiqi.uilib.host.client.font.vanilla.VanillaTextService.Settings;
import club.heiqi.uilib.host.font.IsolatedFontEngine;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextPolicy;
import club.heiqi.uilib.ui.scene.text.font.FontResources;
import club.heiqi.uilib.ui.scene.text.font.FontSource;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontFace;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontSession;
import club.heiqi.uilib.ui.scene.text.font.nativefont.GlyphBitmap;

/** 真实YAML→正式SPI→原版共用解析→FT/HB测绘，无Minecraft类、窗口或GL。 */
public final class GtnhCharacterMappingScenario extends TestApplication {
    // 原二十项来自上游 CustomGlyphs 明确登记；其余十三项来自 issue #53 用户选定的相似替代。
    private static final int[][] EXPECTED = {
        {0xE000, 0x1F701}, {0xE001, 0x1F703}, {0xE002, 0x1F702}, {0xE003, 0x1F704},
        {0xE004, 0x22C0}, {0xE005, 0x22C1}, {0xE006, 0x2742},
        {0xE007, 0x26CF}, {0xE008, 0x21F2}, {0xE009, 0x21F1}, {0xE00A, 0x26A1},
        {0xE00B, 0x2738}, {0xE00C, 0x058D}, {0xE00D, 0x2728}, {0xE00E, 0x1F9F2},
        {0xE00F, 0x2205}, {0xE010, 0x2070}, {0xE011, 0x00B9}, {0xE012, 0x00B2},
        {0xE013, 0x00B3}, {0xE014, 0x2074}, {0xE015, 0x2075}, {0xE016, 0x2076},
        {0xE017, 0x2077}, {0xE018, 0x2078}, {0xE019, 0x2079}, {0xE01A, 0x2080},
        {0xE01B, 0x05D0}, {0xE01C, 0x03C9}, {0xE01D, 0x300C}, {0xE01E, 0x1F70D},
        {0xE01F, 0x2A02}, {0xE020, 0xFE56}
    };
    private Path file;
    private MinecraftHostExtension extension;
    private IntUnaryOperator on, off;

    public static void main(String[] args) throws Exception { new GtnhCharacterMappingScenario().run(); }

    private void write(boolean enabled) throws Exception {
        Files.write(file, ("# 用户注释\ngtnh:\n  mapPrivateCharacters: " + enabled + "\n").getBytes(StandardCharsets.UTF_8));
    }

    private void configuration() throws Exception {
        GtnhCharacterMapping.initialize(file);
        require(Files.isRegularFile(file), "首次加载没有生成独立YAML");
        on = MinecraftHostExtensions.textMapping();
        require(on.applyAsInt(0xE012) == 0x00B2, "默认没有启用准确映射");
        extension = MinecraftHostExtensions.services().stream()
                .filter(value -> value.getClass().getName().equals("club.heiqi.uilib.compat.mc1710.LegacyHostExtension"))
                .findFirst().orElseThrow(() -> new AssertionError("缺少正式SPI provider"));
        extension.resourcesReloaded();
        require(MinecraftHostExtensions.textMapping() == on, "相同配置反复创建映射实例");
        write(false);
        byte[] saved = Files.readAllBytes(file);
        require(MinecraftHostExtensions.textMapping() == on, "普通快照读取隐式访问配置文件");
        extension.resourcesReloaded();
        off = MinecraftHostExtensions.textMapping();
        require(off != on && off.applyAsInt(0xE012) == 0xE012, "资源回调未发布关闭快照");
        extension.resourcesReloaded();
        require(MinecraftHostExtensions.textMapping() == off && Arrays.equals(saved, Files.readAllBytes(file)),
                "关闭快照不稳定或重载改写用户配置");
        for (String invalid : new String[] {"gtnh:\n  mapPrivateCharacters: definitely-not-a-boolean\n", "gtnh:\n  unknown: true\n"}) {
            byte[] broken = invalid.getBytes(StandardCharsets.UTF_8);
            Files.write(file, broken);
            boolean failed = false;
            try { extension.resourcesReloaded(); } catch (IllegalArgumentException expected) { failed = true; }
            require(failed && MinecraftHostExtensions.textMapping() == off
                            && Arrays.equals(broken, Files.readAllBytes(file)),
                    "错误配置没有明确失败，或被默认值静默覆盖");
        }
        java.util.concurrent.atomic.AtomicReference<Throwable> workerFailure = new java.util.concurrent.atomic.AtomicReference<>();
        Thread worker = new Thread(() -> {
            try { GtnhCharacterMapping.reload(); } catch (Throwable failure) { workerFailure.set(failure); }
        });
        worker.start(); worker.join();
        require(workerFailure.get() instanceof IllegalStateException, "非owner发布了配置快照");
        write(true); extension.resourcesReloaded();
        require(MinecraftHostExtensions.textMapping() == on, "重新启用没有复用稳定ON快照");
        // 此运行类路径不含Minecraft客户端；共同配置/Mod入口仍能加载。
        Class.forName("club.heiqi.uilib.compat.CompatMod");
        System.out.println("GTNH_MAPPING_CONFIG default=true reload=true stableSnapshots=true invalidPreserved=true owner=true noMinecraft=true");
    }

    private void exactTable() {
        for (int[] pair : EXPECTED) require(on.applyAsInt(pair[0]) == pair[1], "已选映射不准确: " + Integer.toHexString(pair[0]));
        int changed = 0, unregistered = 0;
        for (int cp = 0; cp <= Character.MAX_CODE_POINT; cp++) {
            if (on.applyAsInt(cp) != cp) changed++;
            require(off.applyAsInt(cp) == cp, "关闭后仍改写字符");
        }
        for (int cp = 0xE000; cp <= 0xE020; cp++) if (on.applyAsInt(cp) == cp) unregistered++;
        require(changed == EXPECTED.length && changed == 33 && unregistered == 0,
                "映射范围未严格覆盖已选三十三项或影响了范围外字符");
        System.out.println("GTNH_MAPPING_TABLE changed=" + changed + " unregistered=" + unregistered + " scalarRangeChecked=true");
    }

    private static List<TextLayout.Glyph> glyphs(TextLayout layout) {
        List<TextLayout.Glyph> values = new ArrayList<>();
        for (TextLayout.Line line : layout.lines()) values.addAll(line.glyphs());
        return values;
    }

    private static Settings settings(IntUnaryOperator mapping) {
        return new Settings(18, 18, 15, true, false, new int[32], mapping);
    }

    private static void supplementarySources(VanillaTextService text, Settings enabled, Settings disabled) {
        // 真实GTNH输入由一个BMP私用字符展开为supplementary；消费原版同一测宽/裁剪/换行入口。
        for (int[] pair : new int[][] {{0xE000, 0x1F701}, {0xE00E, 0x1F9F2}}) {
            String pua = new String(Character.toChars(pair[0]));
            String standard = new String(Character.toChars(pair[1]));
            TextLayout single = text.layout(pua, enabled, -1, 1000, TextPolicy.SINGLE_LINE);
            require(single.logicalText.length() == 2 && single.originalText.equals(pua),
                    "真实GTNH补充码点映射没有保留原始BMP输入");
            for (TextLayout.Glyph glyph : glyphs(single))
                require(glyph.logicalStart == 0 && glyph.logicalEnd == 2
                                && glyph.sourceStart == 0 && glyph.sourceEnd == 1,
                        "真实GTNH补充码点字形丢失原始完整UTF16范围");
            require(text.charWidth(pua.charAt(0), enabled) == text.width(standard, disabled),
                    "BMP原版charWidth没有按补充码点测量");
            String raw = "§n" + pua + " " + pua;
            int limit = (int) Math.ceil(text.layout("§n" + pua, enabled, -1, 1000, TextPolicy.SINGLE_LINE).advanceWidth);
            require(text.trim(raw, limit, false, enabled).equals("§n" + pua)
                            && text.sizeToWidth(raw, limit, enabled) == 3,
                    "真实GTNH补充码点裁剪拆开源字符或返回了映射后的字符");
            List<String> lines = text.list(raw, limit, enabled);
            require(lines.size() == 2, "真实GTNH补充码点换行反例未触达换行");
            for (String line : lines)
                require(line.contains(pua) && !line.contains(standard)
                                && text.layout(line, enabled, -1, 1000, TextPolicy.SINGLE_LINE).logicalText.equals(standard),
                        "真实GTNH补充码点换行没有保留原PUA及活动格式");
            require(text.layout(pua, disabled, -1, 1000, TextPolicy.SINGLE_LINE).logicalText.equals(pua)
                            && single.logicalText.equals(standard),
                    "关闭映射改写原文或污染旧补充码点布局");
        }
        System.out.println("GTNH_MAPPING_SUPPLEMENTARY airMagnet=true rawRanges=true charWidth=true trimSizeWrap=true offFrozen=true");
    }

    private void nativePipeline() throws Exception {
        FontTextService fonts = own(new FontTextService(IsolatedFontEngine.shared()));
        VanillaTextService text = new VanillaTextService(fonts);
        Settings enabled = settings(on), disabled = settings(off);
        int rendered = 0;
        long bytes = 0;
        try (FontSession session = IsolatedFontEngine.shared().openSession()) {
            for (int[] pair : EXPECTED) {
                String raw = new String(Character.toChars(pair[0]));
                String standard = new String(Character.toChars(pair[1]));
                TextLayout actual = text.layout(raw, enabled, -1, 1000, TextPolicy.SINGLE_LINE);
                TextLayout expected = text.layout(standard, disabled, -1, 1000, TextPolicy.SINGLE_LINE);
                require(actual.logicalText.equals(standard) && actual.originalText.equals(raw)
                                && actual.advanceWidth == expected.advanceWidth && text.width(raw, enabled) == text.width(standard, disabled),
                        "原版测宽与真实标准字符排版不一致: " + Integer.toHexString(pair[0]));
                List<TextLayout.Glyph> a = glyphs(actual), e = glyphs(expected);
                require(!a.isEmpty() && a.size() == e.size(), "映射后缺少真实字形");
                for (int i = 0; i < a.size(); i++) {
                    TextLayout.Glyph glyph = a.get(i), reference = e.get(i);
                    require(!glyph.replacement && !reference.replacement && glyph.glyphId != 0
                                    && glyph.glyphId == reference.glyphId
                                    && glyph.source.equals(reference.source) && glyph.xAdvance == reference.xAdvance,
                            "HB整形没有使用对应的真实字体字形");
                    if (pair[0] >= 0xE000 && pair[0] <= 0xE003 || pair[0] == 0xE01E)
                        require(glyph.source.id.equals("noto-symbols"),
                                "炼金字符没有使用随包Noto Sans Symbols来源: " + glyph.source);
                    try (FontFace face = glyph.source.openFace(session, glyph.logicalEm)) {
                        GlyphBitmap bitmap = face.rasterize(glyph.glyphId);
                        boolean ink = false;
                        for (byte pixel : bitmap.pixels()) ink |= pixel != 0;
                        require(bitmap.width > 0 && bitmap.height > 0 && ink, "FT返回空白或占位字形");
                        bytes += bitmap.byteSize();
                    }
                    rendered++;
                }
            }
        }
        supplementarySources(text, enabled, disabled);
        String isotope = "§l\uE000\uE012\uE013\uE015U§r\uE01A\uE00E";
        TextLayout before = text.layout(isotope, enabled, -1, 1000, TextPolicy.SINGLE_LINE);
        require(before.logicalText.equals("\uD83D\uDF01²³⁵U₀\uD83E\uDDF2") && before.originalText.equals(isotope), "多数字同位素组合未逐登记映射");
        write(false); extension.resourcesReloaded();
        TextLayout after = text.layout(isotope, settings(MinecraftHostExtensions.textMapping()), -1, 1000, TextPolicy.SINGLE_LINE);
        require(after.logicalText.equals("\uE000\uE012\uE013\uE015U\uE01A\uE00E") && before.logicalText.equals("\uD83D\uDF01²³⁵U₀\uD83E\uDDF2"),
                "开关复用旧缓存或改写已发布布局");
        long generation = fonts.generation();
        ClassLoader loader = getClass().getClassLoader();
        List<FontSource> next = FontTextService.defaultSources(loader, 2);
        fonts.reload(FontResources.prepare(loader, 2, next, next));
        require(fonts.generation() > generation, "资源重新发布没有推进既有字体排版代际");
        System.out.println("GTNH_MAPPING_NATIVE pairs=" + EXPECTED.length + " glyphs=" + rendered + " bitmapBytes=" + bytes
                + " widthEquivalent=true rawPreserved=true toggleCache=true fontGeneration=true");
    }

    @Override protected void runTests() throws Exception {
        Path directory = Files.createTempDirectory("uilib-gtnh-map-");
        file = directory.resolve("qz_uilib_compat.yaml");
        try {
            scenario("独立YAML默认、资源重载、错误与owner边界", this::configuration);
            scenario("登记与已选相似替代表及全部Unicode关闭恒等", this::exactTable);
            scenario("正式SPI到FT/HB的真实测绘与开关换代", this::nativePipeline);
        } finally {
            Files.deleteIfExists(file); Files.deleteIfExists(directory);
        }
    }
}
