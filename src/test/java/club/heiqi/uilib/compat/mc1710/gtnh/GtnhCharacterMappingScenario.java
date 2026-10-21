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
    // 独立期望来自上游 CustomGlyphs 明确登记的对应关系，含明确的 U+05D0。
    private static final int[][] EXPECTED = {
        {0xE007, 0x26CF}, {0xE008, 0x21F2}, {0xE009, 0x21F1}, {0xE00A, 0x26A1},
        {0xE00F, 0x2205}, {0xE010, 0x2070}, {0xE011, 0x00B9}, {0xE012, 0x00B2},
        {0xE013, 0x00B3}, {0xE014, 0x2074}, {0xE015, 0x2075}, {0xE016, 0x2076},
        {0xE017, 0x2077}, {0xE018, 0x2078}, {0xE019, 0x2079}, {0xE01A, 0x2080},
        {0xE01B, 0x05D0}, {0xE01C, 0x03C9}, {0xE01D, 0x300C}, {0xE01F, 0x2A02}
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
        for (int[] pair : EXPECTED) require(on.applyAsInt(pair[0]) == pair[1], "登记映射不准确: " + Integer.toHexString(pair[0]));
        int changed = 0, unregistered = 0;
        for (int cp = 0; cp <= Character.MAX_CODE_POINT; cp++) {
            if (on.applyAsInt(cp) != cp) changed++;
            require(off.applyAsInt(cp) == cp, "关闭后仍改写字符");
        }
        for (int cp = 0xE000; cp <= 0xE020; cp++) if (on.applyAsInt(cp) == cp) unregistered++;
        require(changed == EXPECTED.length && changed == 20 && unregistered == 13,
                "扩大了未登记的私用字符映射范围");
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
        String isotope = "§l\uE012\uE013\uE015U§r\uE01A";
        TextLayout before = text.layout(isotope, enabled, -1, 1000, TextPolicy.SINGLE_LINE);
        require(before.logicalText.equals("²³⁵U₀") && before.originalText.equals(isotope), "多数字同位素组合未逐登记映射");
        write(false); extension.resourcesReloaded();
        TextLayout after = text.layout(isotope, settings(MinecraftHostExtensions.textMapping()), -1, 1000, TextPolicy.SINGLE_LINE);
        require(after.logicalText.equals("\uE012\uE013\uE015U\uE01A") && before.logicalText.equals("²³⁵U₀"),
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
            scenario("准确登记表及全部Unicode关闭恒等", this::exactTable);
            scenario("正式SPI到FT/HB的真实测绘与开关换代", this::nativePipeline);
        } finally {
            Files.deleteIfExists(file); Files.deleteIfExists(directory);
        }
    }
}
