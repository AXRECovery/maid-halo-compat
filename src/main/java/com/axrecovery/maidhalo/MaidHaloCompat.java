package com.axrecovery.maidhalo;

import com.axrecovery.maidhalo.client.MaidHaloClientSetup;
import com.axrecovery.maidhalo.client.ui.HaloConfigScreen;
import com.axrecovery.maidhalo.config.MaidHaloConfig;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Maid Halo Compat - Touhou Little Maid x Goety: Revelation 光环渲染兼容层。
 *
 * <p>问题：Goety Revelation 的「晋升之环 / 破碎之环 / 终末之环」渲染器只挂在 {@code PlayerRenderer} 上
 * （或对非玩家实体直接短路），因此女仆（EntityMaid）即使 Curios 数据正确也永远不会渲染光环。</p>
 *
 * <p>本模组不修改 Goety / TLM / Curios 的任何字节码（无 Mixin、无 AT），只做两件事：</p>
 * <ol>
 *   <li>通过 TLM 官方扩展 API {@code @LittleMaidExtension} / {@code ILittleMaid} 给女仆渲染器挂上两个渲染层
 *       （GeckoLib 路径 + 传统 Bedrock 模型路径）；</li>
 *   <li>在渲染层里用 Curios API 读女仆的饰品栏，命中光环物品后，用 Goety 自己的模型定义
 *       （反射调用其 {@code createBodyLayer()}）绘制光环，并把「玩家头部变换」替换成「女仆头部骨骼变换」。</li>
 * </ol>
 */
@Mod(MaidHaloCompat.MOD_ID)
public final class MaidHaloCompat {
    public static final String MOD_ID = "maid_halo_compat";

    public MaidHaloCompat() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, MaidHaloConfig.SPEC, "maid_halo_compat-client.toml");
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> MaidHaloCompat::registerClientOnly);

        // 配置加载 / 重载后重新解析「按模型单独配置的终末之环高度」
        MinecraftForge.EVENT_BUS.addListener(MaidHaloCompat::onConfigLoading);
        MinecraftForge.EVENT_BUS.addListener(MaidHaloCompat::onConfigReloading);
    }

    private static void onConfigLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == MaidHaloConfig.SPEC) {
            MaidHaloConfig.reload();
        }
    }

    private static void onConfigReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == MaidHaloConfig.SPEC) {
            MaidHaloConfig.reload();
        }
    }

    /** 仅客户端：客户端初始化 + 注册「Mods → Config」打开的可视化配置页（风格对齐 TLM 配置页）。 */
    private static void registerClientOnly() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(MaidHaloClientSetup::onClientSetup);
        // 打开配置页的快捷键（默认不指定按键）
        FMLJavaModLoadingContext.get().getModEventBus().addListener(
                com.axrecovery.maidhalo.client.HaloKeyBindings::onRegisterKeys);
        MinecraftForge.EVENT_BUS.register(com.axrecovery.maidhalo.client.HaloKeyBindings.class);
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((Screen parent) -> new HaloConfigScreen(parent)));
    }
}
