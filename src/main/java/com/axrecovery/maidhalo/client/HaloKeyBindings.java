package com.axrecovery.maidhalo.client;

import com.axrecovery.maidhalo.client.ui.HaloConfigScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

/**
 * 快捷键：打开本模组的可视化配置页。
 *
 * <p>默认为 **不指定按键**（{@code GLFW_KEY_UNKNOWN}），需要在「控制 → 按键绑定」里自行设置；
 * 打开条件与 TLM 的按键一致：没有其它界面打开时才生效。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class HaloKeyBindings {
    public static final String CATEGORY = "key.categories.maid_halo_compat";
    public static final String OPEN_CONFIG = "key.maid_halo_compat.open_config";

    public static final KeyMapping OPEN_CONFIG_KEY = new KeyMapping(
            OPEN_CONFIG,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            CATEGORY);

    private HaloKeyBindings() {
    }

    /** mod 总线：注册按键。 */
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_CONFIG_KEY);
    }

    /** Forge 总线：按键按下时打开配置页（仅当没有其它界面时）。 */
    @SubscribeEvent
    public static void onKeyInput(net.minecraftforge.client.event.InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null) {
            return;
        }
        while (OPEN_CONFIG_KEY.consumeClick()) {
            minecraft.setScreen(new HaloConfigScreen(null));
        }
    }
}
