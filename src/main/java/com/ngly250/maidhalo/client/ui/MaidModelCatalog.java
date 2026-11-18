package com.ngly250.maidhalo.client.ui;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.ModList;

/**
 * 当前已识别的女仆模型列表（供配置页的 {@code +} 按钮展开选择）。
 *
 * <p>名字优先取 TLM 模型包里的显示名（{@code MaidModelInfo#getName()}），取不到时回退到模型 id。
 * 模型按来源分成三组（配置页里默认全部折叠）：</p>
 * <ol>
 *   <li><b>车万女仆默认模型</b>：游戏目录 {@code tlm_custom_pack/touhou_little_maid-1.0.0}，
 *       用其 {@code pack.mcmeta} 的 {@code description == "touhou project models"} 精确识别；</li>
 *   <li><b>储存在 ysm 文件夹的模型</b>：游戏目录 {@code config/yes_steve_model/custom} 下的每个模型文件夹
 *       （TLM 的 YSM 模型 id 就是文件夹名，显示名取 {@code ysm.json → metadata.name}）；</li>
 *   <li><b>第三方模型</b>：其余（其它模型包 / 整合包自带）。</li>
 * </ol>
 */
public final class MaidModelCatalog {
    private static final boolean TLM_LOADED = ModList.get().isLoaded("touhou_little_maid");
    /** 默认模型包 mcmeta 里的 description。 */
    private static final String DEFAULT_PACK_DESCRIPTION = "touhou project models";

    private static List<Group> cachedGroups;

    private MaidModelCatalog() {
    }

    /** 分组：{@code key} 用于折叠状态，{@code title} 是显示名。 */
    public record Group(String key, Component title, List<Entry> entries) {
    }

    /** @param id 模型 id（如 {@code geckolib:winefox} 或 YSM 的 {@code wine_fox}）
     *  @param name 显示名（取不到时为 id） */
    public record Entry(String id, String name) {
        public String display() {
            return this.name == null || this.name.isBlank() ? this.id : this.name;
        }
    }

    public static List<Group> groups() {
        if (!TLM_LOADED) {
            return Collections.emptyList();
        }
        if (cachedGroups == null) {
            cachedGroups = buildGroups();
        }
        return cachedGroups;
    }

    /** 失效缓存（例如重新打开选择器时）。 */
    public static void invalidate() {
        cachedGroups = null;
    }

    private static List<Group> buildGroups() {
        Set<String> tlmIds;
        try {
            tlmIds = new LinkedHashSet<>(TlmModelSource.ids());
        } catch (Throwable t) {
            tlmIds = new LinkedHashSet<>();
        }

        // 第三方 = 来自「除默认包以外」的其它模型包（tlm_custom_pack 下的目录/zip）。
        // 这样 TLM jar 内置的模型、以及默认包里未写进 maid_model.json 的模型都会留在「默认模型」组，
        // 不会因为磁盘上的默认包条目不完整（例如 geckolib 的若干内置模型）被误判成第三方。
        Set<String> thirdPartyIds = thirdPartyPackIds();
        List<Entry> defaultEntries = new ArrayList<>();
        List<Entry> thirdParty = new ArrayList<>();
        for (String id : tlmIds) {
            Entry entry = new Entry(id, TlmModelSource.name(id));
            if (thirdPartyIds.contains(id)) {
                thirdParty.add(entry);
            } else {
                defaultEntries.add(entry);
            }
        }
        defaultEntries.sort(MaidModelCatalog::compare);
        thirdParty.sort(MaidModelCatalog::compare);

        List<Entry> ysmEntries = ysmEntries();
        ysmEntries.sort(MaidModelCatalog::compare);

        List<Group> groups = new ArrayList<>();
        groups.add(new Group("default",
                Component.translatable("gui.maid_halo_compat.group.default"), defaultEntries));
        groups.add(new Group("ysm",
                Component.translatable("gui.maid_halo_compat.group.ysm"), ysmEntries));
        groups.add(new Group("third_party",
                Component.translatable("gui.maid_halo_compat.group.third_party"), thirdParty));
        return groups;
    }

    private static int compare(Entry a, Entry b) {
        return a.display().compareToIgnoreCase(b.display());
    }

    /** 单个 id 的显示名（配置行也用它；查不到就回退 id）。 */
    public static String displayName(String id) {
        if (!TLM_LOADED || id == null) {
            return id;
        }
        try {
            String name = TlmModelSource.name(id);
            return name == null || name.isBlank() ? id : name;
        } catch (Throwable t) {
            return id;
        }
    }

    // ───────────────────── 默认模型包（按 pack.mcmeta 精确识别） ─────────────────────

    /**
     * 读取 {@code tlm_custom_pack/touhou_little_maid-1.0.0}（目录或同名 zip），
     * 校验 {@code pack.mcmeta} 的 description 为 {@code "touhou project models"}，
     * 返回该包内所有 {@code maid_model.json → model_list[].model_id}。
     */
    static Set<String> defaultPackIds() {
        Set<String> ids = new LinkedHashSet<>();
        Path gameDir = gameDir();
        if (gameDir == null) {
            return ids;
        }
        Path packDir = gameDir.resolve("tlm_custom_pack").resolve("touhou_little_maid-1.0.0");
        try {
            if (Files.isDirectory(packDir)) {
                if (!descriptionMatches(readString(packDir.resolve("pack.mcmeta")))) {
                    return ids;
                }
                Path assets = packDir.resolve("assets");
                if (Files.isDirectory(assets)) {
                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(assets)) {
                        for (Path ns : stream) {
                            if (!Files.isDirectory(ns)) {
                                continue;
                            }
                            Path json = ns.resolve("maid_model.json");
                            if (Files.isRegularFile(json)) {
                                ids.addAll(parseModelIds(readString(json)));
                            }
                        }
                    }
                }
            } else {
                Path zip = gameDir.resolve("tlm_custom_pack").resolve("touhou_little_maid-1.0.0.zip");
                if (!Files.isRegularFile(zip)) {
                    return ids;
                }
                try (ZipFile zf = new ZipFile(zip.toFile())) {
                    if (!descriptionMatches(readZipEntry(zf, "pack.mcmeta"))) {
                        return ids;
                    }
                    var entries = zf.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        String name = entry.getName();
                        if (name.startsWith("assets/") && name.endsWith("/maid_model.json")) {
                            ids.addAll(parseModelIds(readZipEntry(zf, name)));
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // 读取失败就当作没有默认包 → 这些模型会落进第三方组
        }
        return ids;
    }

    private static boolean descriptionMatches(String mcmeta) {
        if (mcmeta == null) {
            return false;
        }
        try {
            var root = com.google.gson.JsonParser.parseString(mcmeta).getAsJsonObject();
            var pack = root.getAsJsonObject("pack");
            if (pack == null || !pack.has("description")) {
                return false;
            }
            return DEFAULT_PACK_DESCRIPTION.equals(pack.get("description").getAsString());
        } catch (Throwable t) {
            return false;
        }
    }

    private static Set<String> parseModelIds(String maidModelJson) {
        Set<String> ids = new LinkedHashSet<>();
        if (maidModelJson == null) {
            return ids;
        }
        try {
            var root = com.google.gson.JsonParser.parseString(maidModelJson).getAsJsonObject();
            var list = root.getAsJsonArray("model_list");
            if (list == null) {
                return ids;
            }
            for (var element : list) {
                var obj = element.getAsJsonObject();
                if (obj.has("model_id")) {
                    ids.add(obj.get("model_id").getAsString());
                }
            }
        } catch (Throwable ignored) {
            // 忽略单个包的解析失败
        }
        return ids;
    }

    /** 默认包在 tlm_custom_pack 下的名字（目录或同名 zip）。 */
    private static final String DEFAULT_PACK_NAME = "touhou_little_maid-1.0.0";

    /**
     * 扫描 {@code tlm_custom_pack} 下**除默认包以外**的所有模型包（目录 / zip），
     * 返回它们登记的 {@code model_id} —— 这些才归入「第三方模型」。
     */
    static Set<String> thirdPartyPackIds() {
        Set<String> ids = new LinkedHashSet<>();
        Path gameDir = gameDir();
        if (gameDir == null) {
            return ids;
        }
        Path root = gameDir.resolve("tlm_custom_pack");
        if (!Files.isDirectory(root)) {
            return ids;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path entry : stream) {
                String name = entry.getFileName().toString();
                if (DEFAULT_PACK_NAME.equals(name) || (DEFAULT_PACK_NAME + ".zip").equals(name)) {
                    continue;   // 默认包本身
                }
                if (name.endsWith(".disabled")) {
                    continue;   // 被禁用的包不会被 TLM 加载
                }
                if (Files.isDirectory(entry)) {
                    Path assets = entry.resolve("assets");
                    if (!Files.isDirectory(assets)) {
                        continue;
                    }
                    try (DirectoryStream<Path> ns = Files.newDirectoryStream(assets)) {
                        for (Path nsDir : ns) {
                            Path json = nsDir.resolve("maid_model.json");
                            if (Files.isRegularFile(json)) {
                                ids.addAll(parseModelIds(readString(json)));
                            }
                        }
                    }
                } else if (name.endsWith(".zip")) {
                    try (ZipFile zf = new ZipFile(entry.toFile())) {
                        var entries = zf.entries();
                        while (entries.hasMoreElements()) {
                            ZipEntry zipEntry = entries.nextElement();
                            String path = zipEntry.getName();
                            if (path.startsWith("assets/") && path.endsWith("/maid_model.json")) {
                                ids.addAll(parseModelIds(readZipEntry(zf, path)));
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // 扫描失败就当没有第三方包（模型全部归入默认组）
        }
        return ids;
    }

    // ───────────────────── YSM 模型（config/yes_steve_model/custom） ─────────────────────

    /** 扫描 {@code config/yes_steve_model/custom/*}：每个子文件夹就是一个 YSM 模型，id = 文件夹名。 */
    static List<Entry> ysmEntries() {
        List<Entry> entries = new ArrayList<>();
        Path gameDir = gameDir();
        if (gameDir == null) {
            return entries;
        }
        Path custom = gameDir.resolve("config").resolve("yes_steve_model").resolve("custom");
        if (!Files.isDirectory(custom)) {
            return entries;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(custom)) {
            for (Path modelDir : stream) {
                if (!Files.isDirectory(modelDir)) {
                    continue;
                }
                String id = modelDir.getFileName().toString();
                entries.add(new Entry(id, readYsmName(modelDir.resolve("ysm.json"), id)));
            }
        } catch (Throwable ignored) {
            // 读不到就当作没有 YSM 模型
        }
        return entries;
    }

    /** {@code ysm.json → metadata.name}，取不到用文件夹名。 */
    private static String readYsmName(Path ysmJson, String fallback) {
        try {
            if (!Files.isRegularFile(ysmJson)) {
                return fallback;
            }
            var root = com.google.gson.JsonParser.parseString(readString(ysmJson)).getAsJsonObject();
            var metadata = root.getAsJsonObject("metadata");
            if (metadata != null && metadata.has("name")) {
                String name = metadata.get("name").getAsString();
                if (name != null && !name.isBlank()) {
                    return name;
                }
            }
        } catch (Throwable ignored) {
            // 回退文件夹名
        }
        return fallback;
    }

    // ───────────────────────────── 工具 ─────────────────────────────

    private static Path gameDir() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            return minecraft == null ? null : minecraft.gameDirectory.toPath();
        } catch (Throwable t) {
            return null;
        }
    }

    private static String readString(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private static String readZipEntry(ZipFile zip, String name) {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null) {
            return null;
        }
        try (var in = zip.getInputStream(entry);
             var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) > 0) {
                sb.append(buffer, 0, read);
            }
            return sb.toString();
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 与 TLM 完全相同的名字解析：模型 JSON 里的 name 是 {@code {model.xxx.name}} 形式的 lang key，
     * 用 TLM 自己的 {@code ParseI18n.getI18nValue(...)}（去花括号 + {@code I18n.get}）翻译；
     * 不是花括号形式（自定义像素包直接写名字）时原样返回。
     */
    private static String translate(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        try {
            return com.github.tartaricacid.touhoulittlemaid.util.ParseI18n.getI18nValue(raw);
        } catch (Throwable t) {
            return raw;
        }
    }

    /** 真正接触 TLM 类的部分：独立类，避免 TLM 缺失时被提前解析。 */
    private static final class TlmModelSource {
        static Set<String> ids() {
            return com.github.tartaricacid.touhoulittlemaid.client.resource.models.MaidModels
                    .getInstance().getModelIdSet();
        }

        static String name(String id) {
            try {
                Optional<com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.MaidModelInfo> info =
                        com.github.tartaricacid.touhoulittlemaid.client.resource.models.MaidModels
                                .getInstance().getInfo(id);
                String raw = info.map(com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.MaidModelInfo::getName)
                        .orElse(id);
                return translate(raw);
            } catch (Throwable t) {
                return id;
            }
        }
    }
}
