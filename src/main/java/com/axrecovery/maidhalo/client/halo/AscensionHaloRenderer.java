package com.axrecovery.maidhalo.client.halo;

import com.axrecovery.maidhalo.config.MaidHaloConfig;
import com.mojang.blaze3d.Blaze3D;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.lang.reflect.Method;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 晋升之环 / 破碎之环在女仆上的绘制。
 *
 * <p>与 Goety Revelation 的 {@code z1gned.goetyrevelation.client.layer.PlayerHaloLayer} 逐句等价，
 * 只有两点不同：</p>
 * <ol>
 *   <li>头部变换由调用方（TLM 女仆头部骨骼）给出，而不是 {@code PlayerModel.head}；</li>
 *   <li>旋转量的来源：玩家侧取自玩家能力 {@code PlayerAbilityHelper#getSpin()}（女仆没有该 capability），
 *       这里改用「真实时间 × 配置角速度」，默认 18°/s，与 RevelationFix 对玩家的覆盖值一致。</li>
 * </ol>
 *
 * <p>模型几何直接复用 Goety 的 {@code PlayerHaloModel.createBodyLayer(...)}（反射调用，避免编译期
 * 依赖整个 Goety 家族），因此外形、贴图、缩放与玩家身上完全一致。</p>
 */
public final class AscensionHaloRenderer {
    private static final Logger LOGGER = LogManager.getLogger("maid_halo_compat");

    private static final ResourceLocation TEXTURE_HALO =
            new ResourceLocation("goety_revelation", "textures/entity/player/halo.png");
    private static final ResourceLocation TEXTURE_BROKEN =
            new ResourceLocation("goety_revelation", "textures/entity/player/broken_halo.png");

    private static ModelPart halo;
    private static ModelPart halo1;
    private static RenderType haloRenderType;
    private static RenderType brokenRenderType;
    private static boolean initialized;
    private static boolean available;

    private AscensionHaloRenderer() {
    }

    /** 预先烘焙模型。幂等，可在客户端初始化阶段调用。 */
    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        if (!CompatMods.isLoaded(CompatMods.GOETY_REVELATION)) {
            available = false;
            LOGGER.debug("未检测到 goety_revelation，跳过晋升之环适配");
            return;
        }
        try {
            Class<?> modelClass = Class.forName(
                    "z1gned.goetyrevelation.client.model.PlayerHaloModel",
                    false,
                    AscensionHaloRenderer.class.getClassLoader());
            Method factory = modelClass.getMethod("createBodyLayer", CubeDeformation.class);
            LayerDefinition definition = (LayerDefinition) factory.invoke(null, new CubeDeformation(0.0F));
            if (definition == null) {
                throw new IllegalStateException("PlayerHaloModel.createBodyLayer 返回 null");
            }
            ModelPart root = definition.bakeRoot();
            ModelPart head = root.getChild("head");
            halo = head.getChild("halo");
            halo1 = halo.getChild("halo1");
            haloRenderType = RenderType.energySwirl(TEXTURE_HALO, 1.0F, 1.0F);
            brokenRenderType = RenderType.energySwirl(TEXTURE_BROKEN, 1.0F, 1.0F);
            available = true;
            LOGGER.debug("已烘焙 Goety 晋升之环模型（PlayerHaloModel）");
        } catch (Throwable t) {
            available = false;
            LOGGER.warn("无法构建 Goety 晋升之环模型，女仆将不渲染该光环", t);
        }
    }

    public static void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, boolean broken) {
        if (!initialized) {
            init();
        }
        if (!available) {
            return;
        }
        RenderType renderType = broken ? brokenRenderType : haloRenderType;
        VertexConsumer consumer = buffer.getBuffer(renderType);

        // 等价于 PlayerHaloLayer：halo.translateAndRotate(...) 后渲染 halo1
        halo.translateAndRotate(poseStack);
        if (!broken) {
            float degrees = (float) ((Blaze3D.getTime() * MaidHaloConfig.spinSpeed()) % 360.0D);
            // 必须用 ZP：halo1 是 XY 平面上的 16x16 面片，盘面法线就是 local Z。
            // 原版 PlayerHaloLayer 用的是 Axis.f_252403_（= ZP，绕法线自转，像轮子）；
            // 若写成 YP 会绕盘内直径旋转，表现为「翻硬币/绕对边中点连线翻转」。
            // 另外 PlayerHaloModel.setupAnim 里也是 halo1.zRot = spin（f_104205_ = zRot），可互相印证。
            poseStack.mulPose(Axis.ZP.rotationDegrees(degrees));
        }
        halo1.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
    }
}
