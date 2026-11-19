package com.axrecovery.maidhalo.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 客户端配置。所有读取都做了「配置尚未加载」时的兜底，避免渲染线程因为 config 未就绪抛异常。
 *
 * <p>偏移量单位为方块（1.0 = 16 个模型像素）。默认全部为 0，即「与玩家身上完全一致」；
 * 若某个女仆模型头骨枢轴与玩家头部不同导致光环位置偏高/偏低，可用偏移量微调。</p>
 */
public final class MaidHaloConfig {
    public static final ForgeConfigSpec SPEC;

    /** 可视化配置页用的注册表；必须在下面的静态块执行前就完成初始化。 */
    private static final List<GuiEntry> GUI_ENTRIES = new ArrayList<>();

    private static final ForgeConfigSpec.BooleanValue ENABLE_ASCENSION;
    private static final ForgeConfigSpec.BooleanValue ENABLE_BROKEN;
    private static final ForgeConfigSpec.BooleanValue ENABLE_END;
    private static final ForgeConfigSpec.DoubleValue SPIN_SPEED;
    private static final ForgeConfigSpec.DoubleValue ASCENSION_OFFSET_X;
    private static final ForgeConfigSpec.DoubleValue ASCENSION_OFFSET_Y;
    private static final ForgeConfigSpec.DoubleValue ASCENSION_OFFSET_Z;
    private static final ForgeConfigSpec.DoubleValue END_OFFSET_X;
    private static final ForgeConfigSpec.DoubleValue END_OFFSET_Y;
    private static final ForgeConfigSpec.DoubleValue END_OFFSET_Z;
    private static final ForgeConfigSpec.DoubleValue HALO_SCALE;
    private static final ForgeConfigSpec.DoubleValue GECKO_EXTRA_YAW;
    private static final ForgeConfigSpec.BooleanValue GECKO_MODEL_SPACE_FLIP;
    private static final ForgeConfigSpec.BooleanValue ENABLE_UNHOLY_HAT;
    private static final ForgeConfigSpec.BooleanValue UNHOLY_HAT_FULL_MODEL;
    private static final ForgeConfigSpec.BooleanValue END_SECOND_PHASE_POST_FX;
    private static final ForgeConfigSpec.DoubleValue END_HALO_LIFT_DEFAULT;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> END_HALO_LIFT_BY_MODEL;
    private static final ForgeConfigSpec.BooleanValue ENABLE_DOMAIN_OF_FADE;
    private static final ForgeConfigSpec.DoubleValue DOMAIN_OF_FADE_OFFSET_X;
    private static final ForgeConfigSpec.DoubleValue DOMAIN_OF_FADE_OFFSET_Y;
    private static final ForgeConfigSpec.DoubleValue DOMAIN_OF_FADE_OFFSET_Z;
    private static final ForgeConfigSpec.BooleanValue ENABLE_BLUE_ARCHIVE_HALO;
    private static final ForgeConfigSpec.DoubleValue BLUE_ARCHIVE_OFFSET_X;
    private static final ForgeConfigSpec.DoubleValue BLUE_ARCHIVE_OFFSET_Y;
    private static final ForgeConfigSpec.DoubleValue BLUE_ARCHIVE_OFFSET_Z;
    private static final ForgeConfigSpec.DoubleValue UNHOLY_HAT_OFFSET_X;
    private static final ForgeConfigSpec.DoubleValue UNHOLY_HAT_OFFSET_Y;
    private static final ForgeConfigSpec.DoubleValue UNHOLY_HAT_OFFSET_Z;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment(
                "Maid Halo Compat - 女仆光环渲染兼容层",
                "Renders Goety Revelation halos (Ascension Halo / Broken Halo / Halo of The-End)",
                "on Touhou Little Maid entities when equipped through Curios.")
                .push("general");

        ENABLE_ASCENSION = builder.comment("渲染晋升之环 (goety_revelation:ascension_halo)")
                .define("enableAscensionHalo", true);
        ENABLE_BROKEN = builder.comment("渲染破碎之环 (goety_revelation:broken_halo)")
                .define("enableBrokenHalo", true);
        ENABLE_END = builder.comment("渲染终末之环 (goety_revelation:halo_of_the_end)")
                .define("enableEndHalo", true);
        SPIN_SPEED = builder.comment("晋升之环自转速度（度/秒）。18.0 与 RevelationFix 对玩家光环的覆盖值一致。")
                .defineInRange("ascensionSpinDegreesPerSecond", 18.0D, 0.0D, 3600.0D);
        HALO_SCALE = builder.comment("光环整体缩放。1.0 = 与玩家一致。")
                .defineInRange("haloScale", 1.0D, 0.05D, 10.0D);

        GECKO_EXTRA_YAW = builder.comment(
                        "仅 GeckoLib / YSM 模型路径：在头部骨骼变换之后额外绕 Y 轴旋转的角度。",
                        "不同女仆模型的 head 骨骼轴向可能不同；若光环出现在脑袋前面/后面与预期相反，填 180。",
                        "默认 0 = 不做额外旋转。")
                .defineInRange("geckoExtraYawDegrees", 0.0D, -180.0D, 180.0D);

        ENABLE_UNHOLY_HAT = builder.comment(
                        "渲染诡厄巫法本体不洁圣冠 (goety:unholy_hat / goety:unholy_hat_halo) 头顶的光环。",
                        "对应 Goety 的 WearRenderer 在 UnholyHatItem 上额外绘制的那层光环（含生命值低于 50% 时的红色版本）。")
                .define("enableUnholyHatHalo", true);

        END_SECOND_PHASE_POST_FX = builder.comment(
                        "终末之环半血（生命值 < 50%）时，复刻玩家侧的 RevelationFix 后处理特效（blur → rewind → odamane）。",
                        "做法与玩家侧完全一致：把光环画进 odamane 专用 render target，再触发帧末后处理合成；",
                        "特效只作用于光环本身的几何，不会影响整个画面。",
                        "关闭后回退到单纯的 energySwirl 通道（旧表现）。")
                .define("enableEndHaloSecondPhasePostFx", true);

        END_HALO_LIFT_DEFAULT = builder.comment(
                        "终末之环基础上抬量（模型空间方块，正值向上，0.5 = 8 像素 = 一个头的高度）。",
                        "作用于所有「未在 endHaloLiftByModel 里单独配置」的模型；酒狐系列还会叠加内置的 +0.5。")
                .defineInRange("endHaloLiftDefault", 0.0D, -4.0D, 4.0D);

        ENABLE_DOMAIN_OF_FADE = builder.comment(
                        "渲染梦幻终焉 (FantasyEnding) 的「泡影之领域」(fantasy_ending:the_domain_of_fade)。",
                        "玩家侧由 Curios 渲染器 DomainOfFadeRenderer 绘制（多边形 / 圆环 / 星形，END_PORTAL 通道）；",
                        "女仆侧直接反射调用同一个渲染器，观感与玩家一致。物品在 back 槽。")
                .define("enableDomainOfFade", true);


        ENABLE_BLUE_ARCHIVE_HALO = builder.comment(
                        "渲染BA光环包 (Blue-Archive-Halos) 的光环（头盔槽物品）。",
                        "女仆侧照搬玩家侧方案：反射复用该模组的 GeckoLib 护甲渲染器（GeoArmorRenderer）。")
                .define("enableBlueArchiveHalo", true);

        END_HALO_LIFT_BY_MODEL = builder.comment(
                        "按模型 id 单独设置终末之环高度。每条格式：\"geckolib:winefox=0.5\"（方块，正值向上）。",
                        "可由游戏内「Mods → Maid Halo Compat → Config」的可视化配置页编辑（独立调整模型的终末之环高度），",
                        "也可以直接手改本文件。模型未出现在这里时：酒狐系列 = 内置 0.5 + endHaloLiftDefault，其余 = endHaloLiftDefault。")
                .defineListAllowEmpty("endHaloLiftByModel", List.of(), o -> o instanceof String);

        UNHOLY_HAT_FULL_MODEL = builder.comment(
                        "true（默认，与玩家侧完全一致）：画整棵 head 子树 —— 帽子本体 + 光环，两层（基础层 + 辉光层）。",
                        "false：只画光环那一支，不画帽子本体。")
                .define("unholyHatRenderFullModel", true);


        GECKO_MODEL_SPACE_FLIP = builder.comment(
                        "仅 GeckoLib / YSM 模型路径：把实体空间（y 向上）转换为原版模型空间（y 向下）。",
                        "TLM 的 GeckoLib 管线不经过 LivingEntityRenderer 的 scale(-1,-1,1)，因此女仆骨骼空间是 y 向上；",
                        "而 Goety 光环模型是按原版模型空间（y 向下）制作的，必须补上这次翻转，否则光环会跑到头部下方。",
                        "默认 true。若某个特殊模型反而因此偏移，可改成 false 对比。")
                .define("geckoModelSpaceFlip", true);

        builder.pop();
        builder.comment("位置微调（方块），施加在女仆头部骨骼变换之后（模型空间：+Y 向下、+Z 向后）。").push("offsets");
        ASCENSION_OFFSET_X = builder.defineInRange("ascensionOffsetX", 0.0D, -4.0D, 4.0D);
        ASCENSION_OFFSET_Y = builder.defineInRange("ascensionOffsetY", 0.0D, -4.0D, 4.0D);
        ASCENSION_OFFSET_Z = builder.defineInRange("ascensionOffsetZ", 0.0D, -4.0D, 4.0D);
        END_OFFSET_X = builder.defineInRange("endHaloOffsetX", 0.0D, -4.0D, 4.0D);
        END_OFFSET_Y = builder.defineInRange("endHaloOffsetY", 0.0D, -4.0D, 4.0D);
        END_OFFSET_Z = builder.defineInRange("endHaloOffsetZ", 0.0D, -4.0D, 4.0D);
        DOMAIN_OF_FADE_OFFSET_X = builder.defineInRange("domainOfFadeOffsetX", 0.0D, -4.0D, 4.0D);
        DOMAIN_OF_FADE_OFFSET_Y = builder.defineInRange("domainOfFadeOffsetY", 0.0D, -4.0D, 4.0D);
        DOMAIN_OF_FADE_OFFSET_Z = builder.defineInRange("domainOfFadeOffsetZ", 0.0D, -4.0D, 4.0D);
        BLUE_ARCHIVE_OFFSET_X = builder.defineInRange("blueArchiveOffsetX", 0.0D, -4.0D, 4.0D);
        BLUE_ARCHIVE_OFFSET_Y = builder.defineInRange("blueArchiveOffsetY", 0.0D, -4.0D, 4.0D);
        BLUE_ARCHIVE_OFFSET_Z = builder.defineInRange("blueArchiveOffsetZ", 0.0D, -4.0D, 4.0D);
        UNHOLY_HAT_OFFSET_X = builder.defineInRange("unholyHatOffsetX", 0.0D, -4.0D, 4.0D);
        UNHOLY_HAT_OFFSET_Y = builder.defineInRange("unholyHatOffsetY", 0.0D, -4.0D, 4.0D);
        UNHOLY_HAT_OFFSET_Z = builder.defineInRange("unholyHatOffsetZ", 0.0D, -4.0D, 4.0D);
        builder.pop();

        SPEC = builder.build();

        // ── 可视化配置页用的注册表（key = 配置文件里的完整路径） ──
        reg("general.enableAscensionHalo", Kind.BOOL, ENABLE_ASCENSION);
        reg("general.enableBrokenHalo", Kind.BOOL, ENABLE_BROKEN);
        reg("general.enableEndHalo", Kind.BOOL, ENABLE_END);
        reg("general.enableUnholyHatHalo", Kind.BOOL, ENABLE_UNHOLY_HAT);
        reg("general.enableDomainOfFade", Kind.BOOL, ENABLE_DOMAIN_OF_FADE);
        reg("general.enableBlueArchiveHalo", Kind.BOOL, ENABLE_BLUE_ARCHIVE_HALO);
        reg("general.unholyHatRenderFullModel", Kind.BOOL, UNHOLY_HAT_FULL_MODEL);
        reg("general.enableEndHaloSecondPhasePostFx", Kind.BOOL, END_SECOND_PHASE_POST_FX);
        reg("general.geckoModelSpaceFlip", Kind.BOOL, GECKO_MODEL_SPACE_FLIP);
        reg("general.ascensionSpinDegreesPerSecond", Kind.DOUBLE, SPIN_SPEED);
        reg("general.haloScale", Kind.DOUBLE, HALO_SCALE);
        reg("general.geckoExtraYawDegrees", Kind.DOUBLE, GECKO_EXTRA_YAW);
        reg("general.endHaloLiftDefault", Kind.DOUBLE, END_HALO_LIFT_DEFAULT);
        reg("general.endHaloLiftByModel", Kind.LIST, END_HALO_LIFT_BY_MODEL);
        reg("offsets.ascensionOffsetX", Kind.DOUBLE, ASCENSION_OFFSET_X);
        reg("offsets.ascensionOffsetY", Kind.DOUBLE, ASCENSION_OFFSET_Y);
        reg("offsets.ascensionOffsetZ", Kind.DOUBLE, ASCENSION_OFFSET_Z);
        reg("offsets.endHaloOffsetX", Kind.DOUBLE, END_OFFSET_X);
        reg("offsets.endHaloOffsetY", Kind.DOUBLE, END_OFFSET_Y);
        reg("offsets.endHaloOffsetZ", Kind.DOUBLE, END_OFFSET_Z);
        reg("offsets.unholyHatOffsetX", Kind.DOUBLE, UNHOLY_HAT_OFFSET_X);
        reg("offsets.unholyHatOffsetY", Kind.DOUBLE, UNHOLY_HAT_OFFSET_Y);
        reg("offsets.unholyHatOffsetZ", Kind.DOUBLE, UNHOLY_HAT_OFFSET_Z);
        reg("offsets.domainOfFadeOffsetX", Kind.DOUBLE, DOMAIN_OF_FADE_OFFSET_X);
        reg("offsets.domainOfFadeOffsetY", Kind.DOUBLE, DOMAIN_OF_FADE_OFFSET_Y);
        reg("offsets.domainOfFadeOffsetZ", Kind.DOUBLE, DOMAIN_OF_FADE_OFFSET_Z);
        reg("offsets.blueArchiveOffsetX", Kind.DOUBLE, BLUE_ARCHIVE_OFFSET_X);
        reg("offsets.blueArchiveOffsetY", Kind.DOUBLE, BLUE_ARCHIVE_OFFSET_Y);
        reg("offsets.blueArchiveOffsetZ", Kind.DOUBLE, BLUE_ARCHIVE_OFFSET_Z);
    }

    /** 配置项类型（可视化配置页据此生成控件）。 */
    public enum Kind {
        BOOL, DOUBLE, LIST
    }

    /** 可视化配置页的一项。 */
    public record GuiEntry(String path, Kind kind, ForgeConfigSpec.ConfigValue<?> value) {
    }

    private static void reg(String path, Kind kind, ForgeConfigSpec.ConfigValue<?> value) {
        GUI_ENTRIES.add(new GuiEntry(path, kind, value));
    }

    /** 全部可可视化编辑的配置项（含 endHaloLiftByModel，配置页对它做特殊 UI）。 */
    public static List<GuiEntry> guiEntries() {
        return Collections.unmodifiableList(GUI_ENTRIES);
    }

    @SuppressWarnings("unchecked")
    public static void guiSet(GuiEntry entry, Object value) {
        try {
            ((ForgeConfigSpec.ConfigValue<Object>) entry.value()).set(value);
        } catch (RuntimeException ignored) {
            // 配置未加载
        }
    }

    public static Object guiGet(GuiEntry entry) {
        try {
            return entry.value().get();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 把当前所有配置值写回文件（配置页「完成」时调用一次）。 */
    public static void saveGui() {
        try {
            SPEC.save();
        } catch (RuntimeException ignored) {
            // 配置未加载
        }
    }

    private MaidHaloConfig() {
    }

    public static boolean enableAscension() {
        return getBoolean(ENABLE_ASCENSION, true);
    }

    public static boolean enableBroken() {
        return getBoolean(ENABLE_BROKEN, true);
    }

    public static boolean enableEnd() {
        return getBoolean(ENABLE_END, true);
    }

    public static float spinSpeed() {
        return (float) getDouble(SPIN_SPEED, 18.0D);
    }

    public static float haloScale() {
        return (float) getDouble(HALO_SCALE, 1.0D);
    }

    public static boolean unholyHatRenderFullModel() {
        return getBoolean(UNHOLY_HAT_FULL_MODEL, true);
    }

    public static boolean enableEndHaloSecondPhasePostFx() {
        return getBoolean(END_SECOND_PHASE_POST_FX, true);
    }

    // ── 按模型单独配置的终末之环高度（endHaloLiftByModel） ──────────────────
    private static volatile Map<String, Float> liftByModel = Collections.emptyMap();
    private static boolean liftParsed;

    public static float endHaloLiftDefault() {
        return (float) getDouble(END_HALO_LIFT_DEFAULT, 0.0D);
    }

    /** 当前所有「按模型覆盖」的终末之环高度（modelId → 方块）。只读。 */
    public static Map<String, Float> endHaloLiftOverrides() {
        ensureLiftParsed();
        return liftByModel;
    }

    /** 该模型的覆盖值；未配置返回 {@code null}。 */
    public static Float endHaloLiftOverride(String modelId) {
        if (modelId == null || modelId.isEmpty()) {
            return null;
        }
        return endHaloLiftOverrides().get(modelId);
    }

    /** 可视化配置页用：写入单个模型的覆盖值（立即落盘）。 */
    public static void setEndHaloLift(String modelId, float value) {
        if (modelId == null || modelId.isEmpty()) {
            return;
        }
        Map<String, Float> next = new LinkedHashMap<>(endHaloLiftOverrides());
        next.put(modelId, value);
        writeLifts(next);
    }

    /** 可视化配置页用：删除某个模型的覆盖（回到默认规则）。 */
    public static void removeEndHaloLift(String modelId) {
        if (modelId == null || modelId.isEmpty()) {
            return;
        }
        Map<String, Float> next = new LinkedHashMap<>(endHaloLiftOverrides());
        if (next.remove(modelId) != null) {
            writeLifts(next);
        }
    }

    /** 可视化配置页「完成」时一次性替换整张表（只落盘一次）。 */
    public static void replaceEndHaloLifts(Map<String, Float> values) {
        writeLifts(new LinkedHashMap<>(values));
    }

    /** 配置加载/重载后调用，重新解析列表。 */
    public static void reload() {
        Map<String, Float> parsed = new LinkedHashMap<>();
        try {
            List<? extends String> raw = END_HALO_LIFT_BY_MODEL.get();
            if (raw != null) {
                for (String entry : raw) {
                    if (entry == null) {
                        continue;
                    }
                    int sep = entry.indexOf('=');
                    if (sep <= 0) {
                        continue;
                    }
                    String id = entry.substring(0, sep).trim();
                    if (id.isEmpty()) {
                        continue;
                    }
                    try {
                        parsed.put(id, Float.parseFloat(entry.substring(sep + 1).trim()));
                    } catch (NumberFormatException ignored) {
                        // 忽略写坏的条目
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // 配置未加载
        }
        liftByModel = Collections.unmodifiableMap(parsed);
        liftParsed = true;
    }

    private static void ensureLiftParsed() {
        if (!liftParsed) {
            reload();
        }
    }

    private static void writeLifts(Map<String, Float> values) {
        List<String> encoded = new ArrayList<>(values.size());
        values.forEach((id, value) -> encoded.add(id + "=" + value));
        try {
            END_HALO_LIFT_BY_MODEL.set(encoded);
            END_HALO_LIFT_BY_MODEL.save();
        } catch (RuntimeException ignored) {
            // 配置未加载
        }
        liftByModel = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        liftParsed = true;
    }





    public static boolean enableBlueArchiveHalo() {
        return getBoolean(ENABLE_BLUE_ARCHIVE_HALO, true);
    }

    public static float blueArchiveOffsetX() {
        return (float) getDouble(BLUE_ARCHIVE_OFFSET_X, 0.0D);
    }

    public static float blueArchiveOffsetY() {
        return (float) getDouble(BLUE_ARCHIVE_OFFSET_Y, 0.0D);
    }

    public static float blueArchiveOffsetZ() {
        return (float) getDouble(BLUE_ARCHIVE_OFFSET_Z, 0.0D);
    }

    public static boolean enableDomainOfFade() {
        return getBoolean(ENABLE_DOMAIN_OF_FADE, true);
    }

    public static float domainOfFadeOffsetX() {
        return (float) getDouble(DOMAIN_OF_FADE_OFFSET_X, 0.0D);
    }

    public static float domainOfFadeOffsetY() {
        return (float) getDouble(DOMAIN_OF_FADE_OFFSET_Y, 0.0D);
    }

    public static float domainOfFadeOffsetZ() {
        return (float) getDouble(DOMAIN_OF_FADE_OFFSET_Z, 0.0D);
    }

    public static boolean enableUnholyHatHalo() {
        return getBoolean(ENABLE_UNHOLY_HAT, true);
    }






    public static float unholyHatOffsetX() {
        return (float) getDouble(UNHOLY_HAT_OFFSET_X, 0.0D);
    }

    public static float unholyHatOffsetY() {
        return (float) getDouble(UNHOLY_HAT_OFFSET_Y, 0.0D);
    }

    public static float unholyHatOffsetZ() {
        return (float) getDouble(UNHOLY_HAT_OFFSET_Z, 0.0D);
    }

    public static boolean geckoModelSpaceFlip() {
        return getBoolean(GECKO_MODEL_SPACE_FLIP, true);
    }

    public static float geckoExtraYawDegrees() {
        return (float) getDouble(GECKO_EXTRA_YAW, 0.0D);
    }

    public static float ascensionOffsetX() {
        return (float) getDouble(ASCENSION_OFFSET_X, 0.0D);
    }

    public static float ascensionOffsetY() {
        return (float) getDouble(ASCENSION_OFFSET_Y, 0.0D);
    }

    public static float ascensionOffsetZ() {
        return (float) getDouble(ASCENSION_OFFSET_Z, 0.0D);
    }

    public static float endOffsetX() {
        return (float) getDouble(END_OFFSET_X, 0.0D);
    }

    public static float endOffsetY() {
        return (float) getDouble(END_OFFSET_Y, 0.0D);
    }

    public static float endOffsetZ() {
        return (float) getDouble(END_OFFSET_Z, 0.0D);
    }

    private static boolean getBoolean(ForgeConfigSpec.BooleanValue value, boolean fallback) {
        try {
            return value.get();
        } catch (RuntimeException e) {
            // 配置尚未加载（例如极端情况下在 config 加载前就触发渲染）
            return fallback;
        }
    }

    private static double getDouble(ForgeConfigSpec.DoubleValue value, double fallback) {
        try {
            return value.get();
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
