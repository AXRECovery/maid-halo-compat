package com.ngly250.maidhalo.client;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 所有直接引用 TLM 类型的代码都集中在这里。
 *
 * <p>只有确认 {@code touhou_little_maid} 已加载后才会调用本类，因此 TLM 缺失时
 * 不会触发 {@link NoClassDefFoundError}（JVM 只在执行到相关指令时才解析类型）。</p>
 */
final class MaidHaloTlmHooks {
    private static final Logger LOGGER = LogManager.getLogger("maid_halo_compat");

    private MaidHaloTlmHooks() {
    }

    /**
     * 兜底：正常情况下 TLM 的注解扫描（{@code @LittleMaidExtension}）已经把扩展塞进
     * {@code TouhouLittleMaid.EXTENSIONS}，这里是「万一没扫到」时的保险。
     *
     * <p>时机是 FMLClientSetupEvent 的 enqueueWork —— 此时 TLM 的 FMLCommonSetupEvent
     * 已经跑完（{@code TouhouLittleMaid.EXTENSIONS} 已被赋值），而女仆渲染器还没创建
     * （渲染器注册在 Minecraft 构造期），所以补注册一定能赶上。</p>
     */
    static void ensureExtensionRegistered() {
        List<ILittleMaid> extensions = TouhouLittleMaid.EXTENSIONS;
        for (ILittleMaid extension : extensions) {
            if (extension instanceof MaidHaloExtension) {
                return;
            }
        }
        extensions.add(new MaidHaloExtension());
        LOGGER.info("[maid_halo_compat] TLM 注解扫描未注册本扩展，已在客户端手动补注册");
    }
}
