package com.axrecovery.maidhalo.client.halo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraftforge.fml.ModList;

/**
 * 兼容目标模组的「存在性」检测。
 *
 * <p>本模组前置只要求车万女仆（TLM）；其余模组（诡厄巫法、启示录、RevelationFix、
 * 梦幻终焉、BA光环包）都是可选的。所有适配代码在运行前都会先经过这里判断，
 * 对应模组没安装时**不会**加载任何相关类、也不会做任何渲染工作。</p>
 */
public final class CompatMods {
    public static final String TLM = "touhou_little_maid";
    public static final String CURIOS = "curios";
    public static final String GOETY = "goety";
    public static final String GOETY_REVELATION = "goety_revelation";
    public static final String REVELATIONFIX = "revelationfix";
    public static final String FANTASY_ENDING = "fantasy_ending";
    public static final String BLUE_ARCHIVE = "minecraftbluearchivehalo";

    private static final Map<String, Boolean> CACHE = new ConcurrentHashMap<>();

    private CompatMods() {
    }

    /** 模组是否存在（结果缓存；模组列表在启动后不会变化）。 */
    public static boolean isLoaded(String modId) {
        if (modId == null || modId.isEmpty()) {
            return false;
        }
        return CACHE.computeIfAbsent(modId, id -> {
            ModList modList = ModList.get();
            return modList != null && modList.isLoaded(id);
        });
    }
}
