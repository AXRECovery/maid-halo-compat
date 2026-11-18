package com.ngly250.maidhalo.client.layer;

import com.ngly250.maidhalo.client.halo.HaloRenderDispatcher;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.processor.ILocationBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.GeoLayerRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.IGeoEntity;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.IGeoEntityRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.ILocationModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.util.RenderUtils;
import com.ngly250.maidhalo.config.MaidHaloConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.joml.Vector3f;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.NotNull;

/**
 * GeckoLib 模型（{@code is_gecko=true}，以及 YSM 模型）路径下的女仆光环层。
 *
 * <p>挂载方式：TLM 扩展 API {@code ILittleMaid#addAdditionGeckoMaidLayer}。
 * TLM 在构造 {@code GeckoEntityMaidRenderer} 时会把它复制进 YSM 渲染器
 * （{@code EntityMaidRenderer#initYsmModelRenderer} → {@code copy(ysmRenderer)}），
 * 所以只要 {@link #copy(IGeoEntityRenderer)} 正确，YSM 模型也能自动获得同一层。</p>
 *
 * <p>头部坐标系：{@code RenderUtils.prepMatrixForLocator(poseStack, model.headBones())}，
 * 与 TLM 自己的 {@code GeckoLayerMaidBipedHead} 完全一致，因此会跟随 GeckoLib 骨骼动画
 * （转头、坐下、待机动作）。</p>
 */
public class MaidHaloGeoLayer<T extends Mob, R extends IGeoEntityRenderer<T>> extends GeoLayerRenderer<T, R> {

    private List<? extends ILocationBone> currentHeadBones;

    public MaidHaloGeoLayer(R renderer) {
        super(renderer);
    }

    @Override
    public GeoLayerRenderer<T, R> copy(R rendererIn) {
        return new MaidHaloGeoLayer<>(rendererIn);
    }


    @Override
    public void render(@NotNull PoseStack poseStack,
                       @NotNull MultiBufferSource buffer,
                       int packedLight,
                       @NotNull T entity,
                       float limbSwing,
                       float limbSwingAmount,
                       float partialTick,
                       float ageInTicks,
                       float netHeadYaw,
                       float headPitch) {
        IGeoEntity geoEntity = this.getGeoEntity(entity);
        if (geoEntity == null) {
            return;
        }
        ILocationModel model = geoEntity.getGeoModel();
        if (model == null) {
            return;
        }
        List<? extends ILocationBone> headBones = model.headBones();
        // 头锁模式需要 head 骨骼；身体锁定模式（终末之环）不需要，所以这里不提前 return，
        // 由 applyHeadTransform 按模式自行判断。
        this.currentHeadBones = headBones;
        HaloRenderDispatcher.render(entity, poseStack, buffer, packedLight, "gecko", ageInTicks,
                this::applyHeadTransform);
    }

    /**
     * 与玩家侧一一对应：
     * <ul>
     *   <li>{@code headLocked = true}（晋升/破碎之环、不洁圣冠）：玩家侧叠加「渲染器自己的 animated head」，
     *       女仆侧等价物 = {@code RenderUtils#prepMatrixForLocator(headBones)}（与 TLM 自己的
     *       {@code GeckoLayerMaidBipedHead} 完全相同的写法）。</li>
     *   <li>{@code headLocked = false}（终末之环）：玩家侧不叠加任何头部变换，基座是「身体空间」
     *       ——原版模型空间的原点在脚上方 1.501 格且 y 向下（{@code LivingEntityRenderer#render} 里
     *       {@code scale(-1,-1,1)} + {@code translate(0,-1.501,0)} 的结果）。
     *       GeckoLib 管线是实体空间（原点在脚、y 向上），所以这里先上移 1.501 再翻转，
     *       得到与玩家完全相同的基座，之后光环的偏移/朝向就与玩家一致。</li>
     * </ul>
     */
    private boolean applyHeadTransform(PoseStack poseStack, boolean headLocked) {
        List<? extends ILocationBone> headBones = this.currentHeadBones;
        if (headLocked) {
            if (headBones == null || headBones.isEmpty()) {
                return false;
            }
            RenderUtils.prepMatrixForLocator(poseStack, headBones);
            float extraYaw = MaidHaloConfig.geckoExtraYawDegrees();
            if (extraYaw != 0.0F) {
                // 不同 GeckoLib / YSM 模型的 head 骨骼轴向不完全一致；默认 0（与玩家一致），必要时用配置修正。
                poseStack.mulPose(Axis.YP.rotationDegrees(extraYaw));
            }
        } else {
            // 等价于 LivingEntityRenderer#render 里的 translate(0, -1.501, 0)（在翻转坐标系中 = 世界向上 1.501）
            poseStack.translate(0.0D, 1.501D, 0.0D);
        }
        if (MaidHaloConfig.geckoModelSpaceFlip()) {
            // 关键：TLM 的 GeckoLib 管线（GeoReplacedEntityRenderer#render）不经过
            // LivingEntityRenderer 的 scale(-1,-1,1)，也不做 translate(0,-1.501,0)，
            // 因此骨骼空间是「实体空间」：x 右、y 向上、z 向后。
            // 而 Goety 的光环模型是按原版「模型空间」制作的：y 向下（x/y 都被 scale(-1,-1,1) 翻过）。
            // 两者相差一次绕 Z 轴 180°，即 scale(-1,-1,1)（行列式为 +1，是纯旋转，不会翻转面朝向）。
            poseStack.scale(-1.0F, -1.0F, 1.0F);
        }
        return true;
    }
}
