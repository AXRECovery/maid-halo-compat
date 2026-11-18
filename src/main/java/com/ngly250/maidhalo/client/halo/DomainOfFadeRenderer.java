package com.ngly250.maidhalo.client.halo;

import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 梦幻终焉（FantasyEnding）「泡影之领域」(fantasy_ending:the_domain_of_fade) 的女仆侧绘制。
 *
 * <p>玩家侧不是模型层，而是一个 Curios 渲染器：
 * {@code com.mega.uom.client.render.curios.DomainOfFadeRenderer}。
 * 它用 {@code RendererUtils#renderRegularPolygon/renderRing/renderStar} 程序化地画
 * 多边形 + 圆环 + 星形（通道来自 {@code MegaRenderType.END_PORTAL(...)} 与 {@code GlowRenderLayer}），
 * 并自己使用全局 buffer source + 时间基准 {@code Client.getCommonDegrees()}。</p>
 *
 * <p>因为它是纯程序化绘制、没有可复用的 ModelPart，所以这里**直接反射调用原渲染器本身**
 * （与玩家侧同一份实现 ⇒ 像素级一致），只负责把 SlotContext 的槽位名与坐标空间对齐：
 * 玩家侧由 {@code CuriosLayer} 在身体空间调用，因此女仆侧也使用「身体锁定」基座。
 * 该方法不使用 RenderLayerParent 与动画参数，这里传 {@code null} / 0。</p>
 */
public final class DomainOfFadeRenderer {
    private static final Logger LOGGER = LogManager.getLogger("maid_halo_compat");

    private static final String RENDERER_CLASS = "com.mega.uom.client.render.curios.DomainOfFadeRenderer";
    /** 物品的 Curios 槽位（梦幻终焉在构造器里声明为 "back"）。 */
    private static final String SLOT = "back";

    private static Object instance;
    private static Method renderMethod;
    private static Constructor<?> slotContextCtor;
    private static boolean initialized;
    private static boolean available;
    private static boolean failureLogged;

    private DomainOfFadeRenderer() {
    }

    /** 启动期预加载（仅当对应模组存在时由客户端初始化调用）。 */
    public static void preload() {
        init();
    }

    private static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        try {
            ClassLoader loader = DomainOfFadeRenderer.class.getClassLoader();

            Class<?> rendererClass = Class.forName(RENDERER_CLASS, false, loader);
            instance = rendererClass.getDeclaredConstructor().newInstance();

            Class<?> slotContext = Class.forName("top.theillusivec4.curios.api.SlotContext", false, loader);
            slotContextCtor = slotContext.getConstructor(
                    String.class, LivingEntity.class, int.class, boolean.class, boolean.class);

            Class<?> layerParent = Class.forName(
                    "net.minecraft.client.renderer.entity.RenderLayerParent", false, loader);
            renderMethod = rendererClass.getMethod("render",
                    ItemStack.class, slotContext, PoseStack.class, layerParent, MultiBufferSource.class,
                    int.class, float.class, float.class, float.class, float.class, float.class, float.class);

            available = true;
            LOGGER.debug("已接入梦幻终焉「泡影之领域」渲染器（DomainOfFadeRenderer）");
        } catch (Throwable t) {
            available = false;
            instance = null;
            renderMethod = null;
            slotContextCtor = null;
            LOGGER.warn("无法接入梦幻终焉「泡影之领域」渲染器，女仆将不渲染该饰品：{}", t.toString());
        }
    }

    public static void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                              LivingEntity entity, ItemStack stack) {
        if (!initialized) {
            init();
        }
        if (!available || stack == null || stack.isEmpty()) {
            return;
        }
        try {
            Object slotContext = slotContextCtor.newInstance(SLOT, entity, 0, false, true);
            // 该渲染器不使用 RenderLayerParent 与动画参数（时间取自 endinglib 的 Client.getCommonDegrees）
            renderMethod.invoke(instance, stack, slotContext, poseStack, null, buffer, packedLight,
                    0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
        } catch (Throwable t) {
            if (!failureLogged) {
                failureLogged = true;
                LOGGER.warn("泡影之领域渲染调用失败（之后不再重复记录）：{}", t.toString());
            }
        }
    }
}
