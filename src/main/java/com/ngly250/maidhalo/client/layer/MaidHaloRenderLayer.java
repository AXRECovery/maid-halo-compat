package com.ngly250.maidhalo.client.layer;

import com.ngly250.maidhalo.client.halo.HaloRenderDispatcher;
import com.github.tartaricacid.simplebedrockmodel.client.bedrock.model.BedrockPart;
import com.github.tartaricacid.touhoulittlemaid.client.model.bedrock.BedrockModel;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.EntityMaidRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.NotNull;

/**
 * 传统 Bedrock 模型（{@code is_gecko=false}，TLM 自带默认模型即属此类）路径下的女仆光环层。
 *
 * <p>挂载方式：TLM 扩展 API {@code ILittleMaid#addAdditionMaidLayer}。</p>
 *
 * <p>头部坐标系：{@code BedrockModel#getHead().translateAndRotate(poseStack)}，
 * 与 TLM 自己的 {@code LayerMaidBipedHead}（原版头盔/头颅渲染）完全相同，
 * 因此随头部摆动、坐下、躺下都会自动跟随。</p>
 */
public class MaidHaloRenderLayer extends RenderLayer<Mob, BedrockModel<Mob>> {

    public MaidHaloRenderLayer(EntityMaidRenderer renderer) {
        super(renderer);
    }


    @Override
    public void render(@NotNull PoseStack poseStack,
                       @NotNull MultiBufferSource buffer,
                       int packedLight,
                       @NotNull Mob entity,
                       float limbSwing,
                       float limbSwingAmount,
                       float partialTick,
                       float ageInTicks,
                       float netHeadYaw,
                       float headPitch) {
        // 不在这里要求 head 骨骼：终末之环是「身体锁定」，与玩家一样完全不需要头部骨骼；
        // 头锁类光环由 applyHeadTransform 自行检查并在缺少 head 时安全跳过。
        HaloRenderDispatcher.render(entity, poseStack, buffer, packedLight, "bedrock", ageInTicks,
                this::applyHeadTransform);
    }

    /**
     * 与玩家侧一一对应：
     * <ul>
     *   <li>{@code headLocked = true}（晋升/破碎之环、不洁圣冠）：玩家侧会把渲染器自己的 animated head
     *       叠上去，女仆侧等价物就是 {@code BedrockModel#getHead().translateAndRotate(...)}
     *       （与 TLM 自己的 {@code LayerMaidBipedHead} 完全相同的写法）。</li>
     *   <li>{@code headLocked = false}（终末之环）：玩家侧不叠加任何头部变换，基座就是身体空间；
     *       传统 Bedrock 路径本身就处在与玩家相同的原版模型空间（都在 {@code LivingEntityRenderer#render}
     *       的 {@code scale(-1,-1,1)} + {@code translate(0,-1.501,0)} 之后），所以这里什么都不用做。</li>
     * </ul>
     */
    private boolean applyHeadTransform(PoseStack poseStack, boolean headLocked) {
        BedrockModel<Mob> model = this.getParentModel();
        if (model == null) {
            return false;
        }
        if (!headLocked) {
            // 身体锁定：基座就是当前层空间，不需要头部骨骼，也不需要任何额外变换
            return true;
        }
        if (!model.hasHead()) {
            return false;
        }
        BedrockPart head = model.getHead();
        if (head == null) {
            return false;
        }
        head.translateAndRotate(poseStack);
        return true;
    }
}
