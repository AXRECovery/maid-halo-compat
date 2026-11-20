package com.axrecovery.maidhalo.client.halo;

import com.axrecovery.maidhalo.config.MaidHaloConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 诡厄巫法本体「不洁圣冠」（{@code goety:unholy_hat} / {@code goety:unholy_hat_halo}）在女仆上的绘制。
 *
 * <p>玩家侧原实现是 Goety 的 {@code com.Polarice3.Goety.client.render.WearRenderer}（Goety 采用 MIT 许可）。
 * 本类只按其**行为**重新实现（未复制其源码、未打包其任何资源）；模型与贴图均在运行时从 Goety 的 jar 读取。</p>
 * <ol>
 *   <li>{@code model.setupAnim(...)}：{@code head.visible = !entity.isInvisible()}、
 *       {@code halo_1.zRot = ageInTicks * 0.01F}；</li>
 *   <li>{@code ICurioRenderer.followBodyRotations(entity, model)}；</li>
 *   <li>基础层：{@code model.renderToBuffer(...)} —— {@code UnholyHatModel#renderToBuffer} 只画
 *       {@code head} 子树（帽子 + 光环），通道 {@code EntityModel#renderType(tex)} = {@code entityCutoutNoCull}，
 *       贴图按物品取 {@code unholy_hat.png} / {@code unholy_hat_halo.png}，血量 ≤ 50% 时换成 {@code _red} 版本；</li>
 *   <li>辉光层（仅当物品是 {@code UnholyHatItem}）：再用 {@code ModRenderType.wraith(unholy_hat_halo[_red].png)}
 *       以全亮 {@code 15728640}、alpha {@code 0.15} 把<b>整棵 head 子树</b>再画一遍。</li>
 * </ol>
 *
 * <p>本类几何直接反射 {@code UnholyHatModel.createBodyLayer()} 得到，因此 UV/偏移与玩家完全一致；
 * 头部变换由调用方（TLM 女仆头部骨骼）给出（对应玩家第 1、2 步把动画 head 喂进模型）。
 * 配置 {@code general.unholyHatRenderFullModel} 控制画「整棵 head 子树（帽子+光环，完全等同玩家）」
 * 还是「只画光环」（默认画整棵，与玩家一致）。</p>
 */
public final class UnholyHatHaloRenderer {
    private static final Logger LOGGER = LogManager.getLogger("maid_halo_compat");

    private static final String NAMESPACE = "goety";
    private static final String PATH_PREFIX = "textures/models/curios/";
    private static final ResourceLocation HAT = new ResourceLocation(NAMESPACE, PATH_PREFIX + "unholy_hat.png");
    private static final ResourceLocation HAT_RED = new ResourceLocation(NAMESPACE, PATH_PREFIX + "unholy_hat_red.png");
    private static final ResourceLocation HAT_HALO = new ResourceLocation(NAMESPACE, PATH_PREFIX + "unholy_hat_halo.png");
    private static final ResourceLocation HAT_HALO_RED = new ResourceLocation(NAMESPACE, PATH_PREFIX + "unholy_hat_halo_red.png");
    /** 辉光层固定用 halo 贴图（与玩家 WearRenderer 一致）。 */
    private static final ResourceLocation GLOW = HAT_HALO;
    private static final ResourceLocation GLOW_RED = HAT_HALO_RED;
    private static final ResourceLocation HALO_VARIANT_ID = ResourceLocation.tryParse("goety:unholy_hat_halo");

    /** 与 Goety 原实现一致：辉光层用的光照值（0xF00000，只吃天光 15）与透明度。 */
    private static final int GLOW_LIGHT = 15728640;
    private static final float GLOW_ALPHA = 0.15F;

    private static ModelPart head;
    private static ModelPart halo;
    private static ModelPart halo1;
    private static Method wraithFactory;
    private static boolean initialized;
    private static boolean available;

    private UnholyHatHaloRenderer() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        if (!CompatMods.isLoaded(CompatMods.GOETY)) {
            available = false;
            LOGGER.debug("未检测到 goety，跳过不洁圣冠光环适配");
            return;
        }
        try {
            ClassLoader loader = UnholyHatHaloRenderer.class.getClassLoader();

            Class<?> modelClass = Class.forName(
                    "com.Polarice3.Goety.client.render.model.UnholyHatModel", false, loader);
            Method factory = modelClass.getMethod("createBodyLayer");
            LayerDefinition definition = (LayerDefinition) factory.invoke(null);
            if (definition == null) {
                throw new IllegalStateException("UnholyHatModel.createBodyLayer 返回 null");
            }
            ModelPart root = definition.bakeRoot();
            head = root.getChild("head");
            halo = head.getChild("halo");
            halo1 = halo.getChild("halo_1");

            // 原实现的辉光层用 Goety 自带的 wraith 通道（eyes shader + 加法混合 + 不剔除）；拿不到就用 vanilla eyes 通道。
            try {
                Class<?> renderTypes = Class.forName(
                        "com.Polarice3.Goety.client.render.ModRenderType", false, loader);
                Method wraith = renderTypes.getMethod("wraith", ResourceLocation.class);
                if (Modifier.isStatic(wraith.getModifiers())) {
                    wraithFactory = wraith;
                }
            } catch (Throwable ignored) {
                wraithFactory = null;
            }

            available = true;
            LOGGER.debug("已烘焙不洁圣冠模型（UnholyHatModel），辉光通道={}", wraithFactory != null ? "goety:wraith" : "vanilla:eyes");
        } catch (Throwable t) {
            available = false;
            LOGGER.warn("无法构建不洁圣冠模型，女仆将不渲染该饰品", t);
        }
    }

    /**
     * @param stack 女仆饰品栏里命中的那一件（用来区分 {@code unholy_hat} / {@code unholy_hat_halo} 的基础贴图）
     */
    public static void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                              LivingEntity entity, float ageInTicks, ItemStack stack) {
        if (!initialized) {
            init();
        }
        if (!available) {
            return;
        }

        // 对应 Goety 的 MobUtil.healthIsHalved：health <= maxHealth / 2 时换红色贴图
        boolean red = entity.getHealth() <= entity.getMaxHealth() / 2.0F;
        // 对应 WearRenderer 注册表里按物品给的贴图：unholy_hat.png vs unholy_hat_halo.png
        boolean haloVariant = stack != null && !stack.isEmpty()
                && HALO_VARIANT_CACHE.computeIfAbsent(stack.getItem(), UnholyHatHaloRenderer::isHaloVariantItem);
        ResourceLocation baseTexture = red
                ? (haloVariant ? HAT_HALO_RED : HAT_RED)
                : (haloVariant ? HAT_HALO : HAT);

        // 对应 UnholyHatModel#setupAnim：光环绕自身法线极慢自转
        halo1.zRot = ageInTicks * 0.01F;

        boolean fullModel = MaidHaloConfig.unholyHatRenderFullModel();
        VertexConsumer baseConsumer = buffer.getBuffer(
                BASE_TYPES.computeIfAbsent(baseTexture, RenderType::entityCutoutNoCull));
        VertexConsumer glowConsumer = buffer.getBuffer(glowType(red));

        if (fullModel) {
            // 完全等同玩家：UnholyHatModel#renderToBuffer 画整棵 head 子树（帽子 + 光环）
            head.render(poseStack, baseConsumer, packedLight, OverlayTexture.NO_OVERLAY);
            head.render(poseStack, glowConsumer, GLOW_LIGHT, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, GLOW_ALPHA);
        } else {
            // 只画光环这一支：head → halo → halo_1 → cube_r1
            halo.translateAndRotate(poseStack);
            halo1.render(poseStack, baseConsumer, packedLight, OverlayTexture.NO_OVERLAY);
            halo1.render(poseStack, glowConsumer, GLOW_LIGHT, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, GLOW_ALPHA);
        }
    }

    private static final java.util.Map<Boolean, RenderType> GLOW_TYPES = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 基础层 RenderType 缓存（贴图 → 类型）。
     *
     * <p>原实现每帧都调 {@code RenderType.entityCutoutNoCull(tex)}：这会**每帧新建一个 RenderType 对象**，
     * 而 {@code MultiBufferSource} 是按 RenderType 实例做键的 —— 等于每帧都换一个新的 buffer，
     * 白白增加内存分配与 draw call。缓存后每张贴图只有一个实例。</p>
     */
    private static final java.util.Map<ResourceLocation, RenderType> BASE_TYPES = new java.util.concurrent.ConcurrentHashMap<>();

    /** 物品 → 是否 halo 变体（避免每帧查注册表）。 */
    private static final java.util.Map<Item, Boolean> HALO_VARIANT_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private static boolean isHaloVariantItem(Item item) {
        return HALO_VARIANT_ID != null && HALO_VARIANT_ID.equals(BuiltInRegistries.ITEM.getKey(item));
    }

    private static RenderType glowType(boolean red) {
        RenderType cached = GLOW_TYPES.get(red);
        if (cached != null) {
            return cached;
        }
        ResourceLocation texture = red ? GLOW_RED : GLOW;
        RenderType created = null;
        if (wraithFactory != null) {
            try {
                created = (RenderType) wraithFactory.invoke(null, texture);
            } catch (Throwable t) {
                LOGGER.warn("调用 goety ModRenderType#wraith 失败，回退到 vanilla eyes 通道", t);
                wraithFactory = null;
            }
        }
        if (created == null) {
            created = RenderType.eyes(texture);
        }
        GLOW_TYPES.put(red, created);
        return created;
    }
}
