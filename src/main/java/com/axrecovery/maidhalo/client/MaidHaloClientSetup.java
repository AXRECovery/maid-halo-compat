package com.axrecovery.maidhalo.client;

import com.axrecovery.maidhalo.client.halo.AscensionHaloRenderer;
import com.axrecovery.maidhalo.client.halo.BlueArchiveHaloRenderer;
import com.axrecovery.maidhalo.client.halo.CompatMods;
import com.axrecovery.maidhalo.client.halo.DomainOfFadeRenderer;
import com.axrecovery.maidhalo.client.halo.EndHaloRenderer;
import com.axrecovery.maidhalo.client.halo.UnholyHatHaloRenderer;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 客户端初始化：环境检查 + 提前烘焙光环模型。
 *
 * <p>本模组对 TLM / Curios / Goety: Revelation / RevelationFix 全部是「软依赖」：
 * 任何一个缺失都只是让对应功能不启用，绝不会阻塞游戏启动。缺失情况会在日志里逐条打印，
 * 方便快速判断「为什么没看到光环」。</p>
 */
public final class MaidHaloClientSetup {
    private static final Logger LOGGER = LogManager.getLogger("maid_halo_compat");

    private MaidHaloClientSetup() {
    }

    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            boolean hasMaid = CompatMods.isLoaded(CompatMods.TLM);
            boolean hasCurios = CompatMods.isLoaded(CompatMods.CURIOS);
            boolean hasGoetyRevelation = CompatMods.isLoaded(CompatMods.GOETY_REVELATION);
            boolean hasRevelationFix = CompatMods.isLoaded(CompatMods.REVELATIONFIX);
            boolean hasGoety = CompatMods.isLoaded(CompatMods.GOETY);
            boolean hasFantasyEnding = CompatMods.isLoaded(CompatMods.FANTASY_ENDING);
            boolean hasBlueArchive = CompatMods.isLoaded(CompatMods.BLUE_ARCHIVE);

            LOGGER.debug("环境检查: TLM={} Curios={} Goety={} GoetyRevelation={} RevelationFix={} FantasyEnding={} BlueArchiveHalos={}",
                    hasMaid, hasCurios, hasGoety, hasGoetyRevelation, hasRevelationFix, hasFantasyEnding, hasBlueArchive);

            // ---- TLM 扩展注册（必须早于女仆渲染器创建）
            if (hasMaid) {
                try {
                    MaidHaloTlmHooks.ensureExtensionRegistered();
                } catch (Throwable t) {
                    LOGGER.error("[maid_halo_compat] 注册 TLM 扩展失败", t);
                }
            } else {
                LOGGER.warn("[maid_halo_compat] 未检测到 touhou_little_maid，女仆光环兼容层不启用");
            }
            if (!hasCurios) {
                LOGGER.warn("[maid_halo_compat] 未检测到 curios，无法读取女仆饰品，光环不会显示");
            }

            // ---- 预烘焙光环模型
            if (hasGoetyRevelation) {
                try {
                    AscensionHaloRenderer.init();
                } catch (Throwable t) {
                    LOGGER.error("[maid_halo_compat] 初始化晋升之环模型失败", t);
                }
            } else {
                LOGGER.debug("[maid_halo_compat] 未检测到 goety_revelation，跳过晋升之环兼容");
            }

            if (hasGoety) {
                try {
                    UnholyHatHaloRenderer.init();
                } catch (Throwable t) {
                    LOGGER.error("[maid_halo_compat] 初始化不洁圣冠光环模型失败", t);
                }
            } else {
                LOGGER.debug("[maid_halo_compat] 未检测到 goety，跳过不洁圣冠光环兼容");
            }

            if (hasFantasyEnding) {
                try {
                    DomainOfFadeRenderer.preload();
                } catch (Throwable t) {
                    LOGGER.error("[maid_halo_compat] 初始化泡影之领域渲染器失败", t);
                }
            } else {
                LOGGER.debug("[maid_halo_compat] 未检测到 fantasy_ending，跳过泡影之领域兼容");
            }

            if (hasBlueArchive) {
                try {
                    BlueArchiveHaloRenderer.preload();
                } catch (Throwable t) {
                    LOGGER.error("[maid_halo_compat] 初始化BA光环渲染器失败", t);
                }
            } else {
                LOGGER.debug("[maid_halo_compat] 未检测到 minecraftbluearchivehalo，跳过BA光环兼容");
            }

            if (hasRevelationFix) {
                try {
                    EndHaloRenderer.init();
                } catch (Throwable t) {
                    LOGGER.error("[maid_halo_compat] 初始化终末之环模型失败", t);
                }
            } else {
                LOGGER.debug("[maid_halo_compat] 未检测到 revelationfix，跳过终末之环兼容");
            }
        });
    }
}
