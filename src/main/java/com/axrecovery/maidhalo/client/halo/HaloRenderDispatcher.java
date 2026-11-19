package com.axrecovery.maidhalo.client.halo;

import com.axrecovery.maidhalo.config.MaidHaloConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * 女仆光环渲染统一入口。两条渲染路径（传统 Bedrock 模型 / GeckoLib 模型）都调用它，
 * 只有「如何得到女仆头部坐标系」不同。
 */
public final class HaloRenderDispatcher {
    private static final Set<String> LOGGED_PATHS = ConcurrentHashMap.newKeySet();

    /**
     * 把 PoseStack 变换到「女仆头部坐标系」。返回 false 表示该模型没有可用的头部骨骼。
     *
     * <p>参数直接对应「玩家侧是怎么画的」：</p>
     * <ul>
     *   <li>{@code headLocked = true}：玩家侧把<b>渲染器自己的 animated head</b> 叠上去
     *       （晋升/破碎之环靠 RevelationFix 的 {@code HaloRendererMixin} 注入；
     *       不洁圣冠靠 {@code WearRenderer} 里的 {@code model.setupAnim(...)} + {@code followBodyRotations}）。
     *       女仆侧等价物 = TLM 女仆渲染器当前的头部骨骼变换。</li>
     *   <li>{@code headLocked = false}：玩家侧根本不叠加头部变换
     *       （终末之环的 {@code OdamaneHaloLayer} 用的是自己那份从没 setupAnim 过的模型，
     *       head 恒为单位变换），基座就是「身体空间」。女仆侧等价物 = 不叠加任何头部变换，
     *       只把 GeckoLib 的实体空间换算成原版模型空间。</li>
     * </ul>
     */
    @FunctionalInterface
    public interface HeadTransform {
        boolean apply(PoseStack poseStack, boolean headLocked);
    }

    private HaloRenderDispatcher() {
    }

    /** 从当前 pose 取出统一缩放（TLM GeckoLib 女仆模型的 render_entity_scale）。 */
    private static float uniformScale(PoseStack poseStack) {
        var m = poseStack.last().pose();
        float x = m.m00();
        float y = m.m01();
        float z = m.m02();
        float len = (float) Math.sqrt(x * x + y * y + z * z);
        return len > 1.0E-5F ? len : 1.0F;
    }

    /** 女仆模型 id（诊断用）。 */
    private static String modelId(net.minecraft.world.entity.LivingEntity entity) {
        if (!(entity instanceof net.minecraft.world.entity.Mob mob)) {
            return "?";
        }
        var maid = com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid.convert(mob);
        return maid == null ? "?" : String.valueOf(maid.getModelId());
    }

    /**
     * @param path 渲染路径标签（"bedrock" / "gecko"）：两条管线的空间轴向不同。
     */
    public static void render(LivingEntity entity,
                              PoseStack poseStack,
                              MultiBufferSource buffer,
                              int packedLight,
                              String path,
                              float ageInTicks,
                              HeadTransform headTransform) {
        if (entity == null || entity.isInvisible()) {
            return;
        }
        HaloSources.EquippedHalo equipped = HaloSources.findEquippedHalo(entity);
        HaloKind kind = equipped.kind();
        if (kind == HaloKind.NONE || !kind.enabled()) {
            return;
        }

        // 与玩家侧一一对应：
        //   终末之环 / 泡影之领域 = 身体锁定（玩家侧分别在 OdamaneHaloLayer、Curios 层绘制，不带头部变换）
        //   晋升/破碎之环、不洁圣冠、BA光环 = 头锁
        //   BA光环：与晋升之环用同一个头部基座即可 —— GeoArmorRenderer 内部的
        //   T = translate(0,1.5,0)·scale(-1,-1,1) 会把 armor 几何里「头在 y=1.5」映射到模型空间原点，
        //   也就是我们 pose 所在的头部位置，不需要再额外变换（额外变换正是之前跑偏的原因）。
        boolean headLocked = kind != HaloKind.END && kind != HaloKind.DOMAIN_OF_FADE;

        poseStack.pushPose();
        try {
            if (!headTransform.apply(poseStack, headLocked)) {
                return;
            }
            float scale = MaidHaloConfig.haloScale();
            if (scale != 1.0F) {
                poseStack.scale(scale, scale, scale);
            }
            if (kind.isAscensionFamily()) {
                poseStack.translate(MaidHaloConfig.ascensionOffsetX(),
                        MaidHaloConfig.ascensionOffsetY(),
                        MaidHaloConfig.ascensionOffsetZ());
                AscensionHaloRenderer.render(poseStack, buffer, packedLight, kind == HaloKind.BROKEN_ASCENSION);
            } else if (kind == HaloKind.END) {
                poseStack.translate(MaidHaloConfig.endOffsetX(),
                        MaidHaloConfig.endOffsetY(),
                        MaidHaloConfig.endOffsetZ());
                EndHaloRenderer.render(poseStack, buffer, entity);
            } else if (kind == HaloKind.BLUE_ARCHIVE_HALO) {
                // 基线校准：世界空间向上 0.5、向前 0.3（模型空间 +y 向下、+z 向后）。
                // 关键：女仆 pose 上带有 render_entity_scale（酒狐类 0.65），pose 里的位移会被一起缩放，
                // 所以这里除以 s 换算，保证**世界**位移对所有模型都相同（大模型 s≈1 不变，小模型按比例放大）。
                float baScale = uniformScale(poseStack);
                // 上一轮“再抬半个头 + 后移”的幅度取一半：
                //   高度 0.5 → 0.75 的变化取半 ⇒ 0.625；前后 -0.3 → 0.2 的变化取半 ⇒ -0.05
                poseStack.translate(0.0D, -0.625D / baScale, -0.05D / baScale);
                poseStack.translate(MaidHaloConfig.blueArchiveOffsetX(),
                        MaidHaloConfig.blueArchiveOffsetY(),
                        MaidHaloConfig.blueArchiveOffsetZ());
                BlueArchiveHaloRenderer.render(poseStack, buffer, packedLight, entity, equipped.stack());
            } else if (kind == HaloKind.DOMAIN_OF_FADE) {
                poseStack.translate(MaidHaloConfig.domainOfFadeOffsetX(),
                        MaidHaloConfig.domainOfFadeOffsetY(),
                        MaidHaloConfig.domainOfFadeOffsetZ());
                DomainOfFadeRenderer.render(poseStack, buffer, packedLight, entity, equipped.stack());
            } else if (kind == HaloKind.UNHOLY_HAT) {
                poseStack.translate(MaidHaloConfig.unholyHatOffsetX(),
                        MaidHaloConfig.unholyHatOffsetY(),
                        MaidHaloConfig.unholyHatOffsetZ());
                UnholyHatHaloRenderer.render(poseStack, buffer, packedLight, entity, ageInTicks, equipped.stack());
            }
        } finally {
            poseStack.popPose();
        }
    }
}
