package com.axrecovery.maidhalo.client.ui;

import java.lang.reflect.Method;
import java.util.Locale;
import net.minecraftforge.fml.ModList;

/**
 * 搜索匹配工具：装了「通用拼音搜索（JECh, {@code jecharacters}）」时借用它的拼音匹配，
 * 没装时退化为不区分大小写的子串匹配。
 *
 * <p>JECh 不在编译期依赖里（本模组构建只要求车万女仆 + Curios），所以这里用反射调用
 * {@code me.towdium.jecharacters.utils.Match#contains(String, CharSequence)}；
 * 缺失或调用失败时自动回退，不影响搜索本身。</p>
 */
public final class PinyinMatch {
    /** JECh 的 modId。 */
    private static final String JECH_MOD_ID = "jecharacters";

    private static boolean initialized;
    private static Method jechContains;

    private PinyinMatch() {
    }

    private static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        try {
            if (!ModList.get().isLoaded(JECH_MOD_ID)) {
                return;
            }
            Class<?> match = Class.forName("me.towdium.jecharacters.utils.Match", false,
                    PinyinMatch.class.getClassLoader());
            jechContains = match.getMethod("contains", String.class, CharSequence.class);
        } catch (Throwable ignored) {
            jechContains = null;
        }
    }

    /** {@code text} 是否匹配 {@code query}（支持拼音/首字母，取决于 JECh 是否安装）。 */
    public static boolean matches(String text, String query) {
        if (text == null || query == null) {
            return false;
        }
        String q = query.trim();
        if (q.isEmpty()) {
            return true;
        }
        init();
        if (jechContains != null) {
            try {
                Object result = jechContains.invoke(null, text, q);
                if (result instanceof Boolean matched) {
                    return matched;
                }
            } catch (Throwable ignored) {
                // 反射失败 → 回退
            }
        }
        return text.toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT));
    }
}
