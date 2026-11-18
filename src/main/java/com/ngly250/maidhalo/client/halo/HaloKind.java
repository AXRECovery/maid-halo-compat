package com.ngly250.maidhalo.client.halo;

import com.ngly250.maidhalo.config.MaidHaloConfig;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 女仆身上可渲染的光环类型。
 *
 * <p>识别刻意采用「类名 + 注册名」双重判定，而不是直接 {@code instanceof}：</p>
 * <ul>
 *   <li>类名判定用 {@code Class.forName(...).isInstance(item)}，不会在 Goety/RevelationFix
 *       缺失时触发 {@link NoClassDefFoundError}，也兼容其子类；</li>
 *   <li>注册名判定作为兜底，防止将来物品类被重命名。</li>
 * </ul>
 */
public enum HaloKind {
    NONE("", ""),
    /** 晋升之环（goety_revelation:ascension_halo），玩家侧由 PlayerHaloLayer 渲染。 */
    ASCENSION("z1gned.goetyrevelation.item.AscensionHalo", "goety_revelation:ascension_halo"),
    /** 破碎之环（goety_revelation:broken_halo），玩家侧同样由 PlayerHaloLayer 渲染。 */
    BROKEN_ASCENSION("z1gned.goetyrevelation.item.BrokenAscensionHalo", "goety_revelation:broken_halo"),
    /** 终末之环（goety_revelation:halo_of_the_end），玩家侧由 RevelationFix 的 OdamaneHaloLayer 渲染。 */
    END("com.mega.revelationfix.common.item.curios.OdamaneHalo", "goety_revelation:halo_of_the_end"),
    /**
     * 不洁圣冠（goety:unholy_hat / goety:unholy_hat_halo，本体模组 Goety）。
     * 玩家侧由 Goety 的 {@code WearRenderer} 渲染：先画一遍帽子本体，再用 wraith 通道以 alpha 0.15
     * 叠一层 {@code unholy_hat_halo.png} 的辉光；生命值 ≤ 50% 时换成 {@code unholy_hat_halo_red.png}。
     * 两个物品共用 {@code UnholyHatItem} 类，所以按类名判定即可同时覆盖。
     */
    UNHOLY_HAT("com.Polarice3.Goety.common.items.curios.UnholyHatItem", "goety:unholy_hat"),
    /**
     * 泡影之领域（fantasy_ending:the_domain_of_fade，梦幻终焉 FantasyEnding）。
     * 玩家侧是 Curios 的 {@code ICurioRenderer}：{@code com.mega.uom.client.render.curios.DomainOfFadeRenderer}
     * 直接用 {@code RendererUtils} 画多边形/圆环/星形（END_PORTAL 与 GlowRenderLayer 通道），挂在 back 槽。
     */
    DOMAIN_OF_FADE("com.mega.uom.common.items.curios.TheDomainOfFadeCurio", "fantasy_ending:the_domain_of_fade"),
    /**
     * 碧蓝档案光环包（Blue-Archive-Halos）：物品是**头盔护甲**
     * （{@code HaloItem extends ArmorItem(HELMET)} 且实现 GeckoLib 的 {@code GeoItem}），
     * 玩家侧由 {@code IClientItemExtensions#getHumanoidArmorModel} 返回 GeckoLib 的 {@code GeoArmorRenderer}，
     * 交给原版 {@code HumanoidArmorLayer} 渲染。女仆没有护甲层，因此由本补丁在头盔槽检测并复用同一个渲染器。
     */
    BLUE_ARCHIVE_HALO("com.spicywolf.minecraftbluearchivehalo.item.custom.HaloItem", "");

    private static final Map<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> MISSING = Collections.newSetFromMap(new ConcurrentHashMap<>());
    /** 物品 → 光环类型（物品集合很小且不变，缓存后每帧只剩一次 Map 查询）。 */
    private static final Map<Item, HaloKind> ITEM_CACHE = new ConcurrentHashMap<>();
    private static final HaloKind[] RENDERABLE = {ASCENSION, BROKEN_ASCENSION, END, UNHOLY_HAT, DOMAIN_OF_FADE, BLUE_ARCHIVE_HALO};

    private final String className;
    private final ResourceLocation registryName;

    HaloKind(String className, String registryName) {
        this.className = className;
        // 用 tryParse 而不是 new ResourceLocation(String)：后者在 Forge 1.20.1 已标记 @Deprecated
        this.registryName = registryName.isEmpty() ? null : ResourceLocation.tryParse(registryName);
    }

    public static HaloKind of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return NONE;
        }
        // 关键优化：原先每帧都要做 BuiltInRegistries.ITEM.getKey(...)（会分配/查表）+ 最多 3 次 isInstance，
        // 女仆身上每件非光环饰品都会走一遍。物品数量有限，按 Item 缓存结果即可，行为完全一致。
        return ITEM_CACHE.computeIfAbsent(stack.getItem(), HaloKind::resolve);
    }

    private static HaloKind resolve(Item item) {
        for (HaloKind kind : RENDERABLE) {
            if (kind.matches(item)) {
                return kind;
            }
        }
        return NONE;
    }

    public boolean matches(Item item) {
        Class<?> type = resolveClass(this.className);
        if (type != null && type.isInstance(item)) {
            return true;
        }
        return this.registryName != null && this.registryName.equals(BuiltInRegistries.ITEM.getKey(item));
    }

    private static Class<?> resolveClass(String className) {
        if (className.isEmpty()) {
            return null;
        }
        Class<?> cached = CLASS_CACHE.get(className);
        if (cached != null) {
            return cached;
        }
        if (MISSING.contains(className)) {
            return null;
        }
        try {
            Class<?> clazz = Class.forName(className, false, HaloKind.class.getClassLoader());
            CLASS_CACHE.put(className, clazz);
            return clazz;
        } catch (ClassNotFoundException | LinkageError e) {
            MISSING.add(className);
            return null;
        }
    }

    /** 该光环需要哪个模组（缺失时整条适配链不启用）。 */
    public String requiredMod() {
        return switch (this) {
            case ASCENSION, BROKEN_ASCENSION -> CompatMods.GOETY_REVELATION;
            case END -> CompatMods.REVELATIONFIX;
            case UNHOLY_HAT -> CompatMods.GOETY;
            case DOMAIN_OF_FADE -> CompatMods.FANTASY_ENDING;
            case BLUE_ARCHIVE_HALO -> CompatMods.BLUE_ARCHIVE;
            case NONE -> null;
        };
    }

    public boolean enabled() {
        String mod = requiredMod();
        if (mod != null && !CompatMods.isLoaded(mod)) {
            return false;
        }
        return switch (this) {
            case ASCENSION -> MaidHaloConfig.enableAscension();
            case BROKEN_ASCENSION -> MaidHaloConfig.enableBroken();
            case END -> MaidHaloConfig.enableEnd();
            case UNHOLY_HAT -> MaidHaloConfig.enableUnholyHatHalo();
            case DOMAIN_OF_FADE -> MaidHaloConfig.enableDomainOfFade();
            case BLUE_ARCHIVE_HALO -> MaidHaloConfig.enableBlueArchiveHalo();
            case NONE -> false;
        };
    }

    public boolean isAscensionFamily() {
        return this == ASCENSION || this == BROKEN_ASCENSION;
    }
}
