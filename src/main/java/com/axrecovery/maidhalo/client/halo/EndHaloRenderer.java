package com.axrecovery.maidhalo.client.halo;

import com.axrecovery.maidhalo.config.MaidHaloConfig;
import com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.Blaze3D;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 终末之环（halo_of_the_end）在女仆上的绘制。
 *
 * <p>与 RevelationFix 的 {@code com.mega.revelationfix.common.odamane.client.OdamaneHaloLayer#render}
 * 保持相同的变换顺序：头部变换 → {@code Halo} 节点变换 → 平移 (0,0,-0.5) → 旋转 bone2..bone5 →
 * 渲染 {@code Halo}（ModelPart.render 内部会再应用一次自身变换，与原实现一致）。</p>
 *
 * <p>与玩家侧的关系：变换链与玩家 {@code OdamaneHaloLayer} 逐句一致（不叠加头部变换、只锁身体），
 * 通道选择也一致（首阶段 {@code entityCutoutNoCull}，二阶段切 {@code energySwirl}）。
 * 唯一不可移植的是二阶段的后期特效：玩家默认走 {@code GRRenderTypes.getOdamane} +
 * {@code PostEffectRegistry}（需要逐帧绑定后期渲染 target），第三方渲染层拿不到 target，因此用它的降级通道。</p>
 */
public final class EndHaloRenderer {
    private static final Logger LOGGER = LogManager.getLogger("maid_halo_compat");

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("revelationfix", "textures/entity/player/halo_the_end.png");
    private static final int FULL_BRIGHT = 15728880;

    /**
     * 酒狐（winefox）系列模型专用的整体上抬量：一个头的高度。
     *
     * <p>0.5 方块 = 8 像素 = 标准头高（层空间里会随模型 scale 等比生效）。
     * 原因见 README 5.8：终末之环的高度是按玩家比例写死的常数（脚上方 34px），而酒狐系列模型的
     * 头部枢轴在所有内置 GeckoLib 模型里最高（31.4–33.6px），于是同一高度落在下巴/嘴附近。</p>
     */
    private static final float WINE_FOX_HEAD_LIFT = 0.5F;

    // ── 半血后处理：RevelationFix 的 odamane 管线（纯反射，无编译期依赖）──────────────
    /** 玩家二阶段使用的通道：GRRenderTypes.getOdamane(TEXTURE)，输出到 odamane 专用 render target。 */
    private static RenderType odamaneType;
    /** 光影包分支用的 eyesAlpha 通道：MegaRenderType.getEyesAlphaEnabled(TEXTURE)。 */
    private static RenderType eyesPulseType;
    /** ClientProxy.ODAMANE_SHADER（ResourceLocation）。 */
    private static Object odamaneShaderId;
    /** PostEffectRegistry.renderEffectForNextTick(ResourceLocation)。 */
    private static Method postEffectTrigger;
    /** PostEffectRegistry.getRenderTargetFor(ResourceLocation)，用于确认后处理 target 已就绪。 */
    private static Method postEffectTargetLookup;
    /** SafeClass.usingShaderPack()。 */
    private static Method shaderPackProbe;
    /** ClientConfig.enableSpecialHaloEffect（对应玩家侧同名开关）。 */
    private static Field specialHaloEffectFlag;
    private static boolean postFxResolved;
    private static boolean postFxLogged;
    /** 上一次实际走的分支（-1 未知 / 0 降级 / 1 后处理），仅用于切换时打一条日志。 */
    private static int loggedBranch = -1;

    /** 模型 id → 是否酒狐系列（避免每帧做字符串切分/比较）。 */
    private static final java.util.Map<String, Boolean> WINE_FOX_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private static ModelPart halo;
    private static ModelPart bone;
    private static ModelPart bone2;
    private static ModelPart bone3;
    private static ModelPart bone4;
    private static ModelPart bone5;
    /** 玩家侧 vanilla 通道：EntityModel#renderType(...) = entityCutoutNoCull（首阶段用）。 */
    private static RenderType cutoutType;
    /** 玩家侧 shader 通道：RenderType.energySwirl（二阶段 / 光影包降级用）。 */
    private static RenderType swirlType;
    private static boolean initialized;
    private static boolean available;

    private EndHaloRenderer() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        if (!CompatMods.isLoaded(CompatMods.REVELATIONFIX)) {
            available = false;
            LOGGER.debug("未检测到 revelationfix，跳过终末之环适配");
            return;
        }
        try {
            Class<?> modelClass = Class.forName(
                    "com.mega.revelationfix.common.odamane.client.OdamaneHaloModel",
                    false,
                    EndHaloRenderer.class.getClassLoader());
            Method factory = modelClass.getMethod("createBodyLayer");
            LayerDefinition definition = (LayerDefinition) factory.invoke(null);
            if (definition == null) {
                throw new IllegalStateException("OdamaneHaloModel.createBodyLayer 返回 null");
            }
            ModelPart root = definition.bakeRoot();
            ModelPart head = root.getChild("head");
            halo = head.getChild("Halo");
            bone = halo.getChild("bone");
            bone2 = halo.getChild("bone2");
            bone3 = halo.getChild("bone3");
            bone4 = halo.getChild("bone4");
            bone5 = halo.getChild("bone5");
            cutoutType = RenderType.entityCutoutNoCull(TEXTURE);
            swirlType = RenderType.energySwirl(TEXTURE, 1.0F, 1.0F);
            available = true;
            LOGGER.debug("已烘焙终末之环模型（OdamaneHaloModel）");
            initSecondPhasePostFx();
        } catch (Throwable t) {
            available = false;
            LOGGER.warn("无法构建终末之环模型，女仆将不渲染该光环", t);
        }
    }

    /**
     * 解析 RevelationFix 的 odamane 后处理接口（全部是公开成员，纯反射）。
     *
     * <p>玩家侧二阶段的真实链路（对照 RevelationFix 4.9 反编译确认）：</p>
     * <ol>
     *   <li>用 {@code GRRenderTypes.getOdamane(tex)} 把光环画进该后处理链的 {@code "final"} target；
     *       该 RenderType 的 {@code OutputStateShard} 负责绑定/恢复 target；</li>
     *   <li>{@code PostEffectRegistry.renderEffectForNextTick(ODAMANE_SHADER)} 把这条链标记为本帧启用；</li>
     *   <li>{@code LevelRendererMixin} 在 renderLevel 结束调用 {@code processEffects}（blur → rewind → odamane）
     *       并 {@code blitEffects} 以加法混合叠回屏幕 —— 特效只覆盖画进 target 的几何，不影响其他画面。</li>
     * </ol>
     */
    private static void initSecondPhasePostFx() {
        if (postFxResolved) {
            return;
        }
        postFxResolved = true;
        String root = "com.mega.revelationfix.";
        try {
            ClassLoader loader = EndHaloRenderer.class.getClassLoader();

            Class<?> renderTypes = Class.forName(root + "client.citadel.GRRenderTypes", false, loader);
            odamaneType = (RenderType) renderTypes.getMethod("getOdamane", ResourceLocation.class)
                    .invoke(null, TEXTURE);

            Class<?> registry = Class.forName(root + "client.citadel.PostEffectRegistry", false, loader);
            postEffectTrigger = registry.getMethod("renderEffectForNextTick", ResourceLocation.class);
            postEffectTargetLookup = registry.getMethod("getRenderTargetFor", ResourceLocation.class);

            odamaneShaderId = Class.forName(root + "proxy.ClientProxy", false, loader)
                    .getField("ODAMANE_SHADER").get(null);

            shaderPackProbe = Class.forName(root + "common.compat.SafeClass", false, loader)
                    .getMethod("usingShaderPack");

            specialHaloEffectFlag = Class.forName(root + "common.config.ClientConfig", false, loader)
                    .getField("enableSpecialHaloEffect");

            eyesPulseType = (RenderType) Class.forName(root + "client.renderer.MegaRenderType", false, loader)
                    .getMethod("getEyesAlphaEnabled", ResourceLocation.class).invoke(null, TEXTURE);
        } catch (Throwable t) {
            odamaneType = null;
            eyesPulseType = null;
            odamaneShaderId = null;
            postEffectTrigger = null;
            postEffectTargetLookup = null;
            shaderPackProbe = null;
            specialHaloEffectFlag = null;
            LOGGER.warn("终末之环半血后处理不可用，回退到 energySwirl 通道：{}", t.toString());
        }
    }

    private static boolean usingShaderPack() {
        if (shaderPackProbe == null) {
            return false;
        }
        try {
            return (boolean) shaderPackProbe.invoke(null);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean specialHaloEffectEnabled() {
        if (specialHaloEffectFlag == null) {
            return true;
        }
        try {
            return specialHaloEffectFlag.getBoolean(null);
        } catch (Throwable t) {
            return true;
        }
    }

    private static boolean postEffectTargetReady() {
        if (postEffectTargetLookup == null || odamaneShaderId == null) {
            return false;
        }
        try {
            return postEffectTargetLookup.invoke(null, odamaneShaderId) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 二阶段（血量 &lt; 50%）绘制，按玩家侧三条分支逐一对齐：
     * <ol>
     *   <li>未开光影包 + 开启「特殊光环特效」→ odamane 后处理（画进 target + 触发帧末合成）；</li>
     *   <li>开光影包 → energySwirl 主层 + eyesAlpha 加法层（玩家侧此处 alpha 固定 2.0）；</li>
     *   <li>关闭特殊特效 / 反射接口缺失 → 仅 energySwirl（旧表现）。</li>
     * </ol>
     */
    private static void renderSecondPhase(PoseStack poseStack, MultiBufferSource buffer) {
        boolean shaderPack = usingShaderPack();
        // 玩家侧用的是全局 buffer source（Minecraft#renderBuffers#bufferSource），这里保持一致：
        // 既避免「TLM 传入的 buffer 可能是别的实例」导致 flush 落空，也让 target 绑定/flush 时机完全同构。
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();

        boolean postFx = !shaderPack
                && MaidHaloConfig.enableEndHaloSecondPhasePostFx()
                && specialHaloEffectEnabled()
                && odamaneType != null
                && postEffectTrigger != null
                && odamaneShaderId != null
                && postEffectTargetReady();

        if (postFx) {
            try {
                // 与玩家侧同序：取通道 → 触发本帧后处理 → 绘制 → 立即 flush（odamane 的 OutputState 负责绑定 target）
                VertexConsumer consumer = bufferSource.getBuffer(odamaneType);
                postEffectTrigger.invoke(null, odamaneShaderId);
                halo.render(poseStack, consumer, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                bufferSource.endBatch();
                Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
                logBranch(true, null);
                if (!postFxLogged) {
                    postFxLogged = true;
                    LOGGER.debug("终末之环半血后处理已启用（odamane 管线）");
                }
                return;
            } catch (Throwable t) {
                LOGGER.warn("终末之环半血后处理调用失败，本帧回退到 energySwirl：{}", t.toString());
            }
        }

        logBranch(false, shaderPack ? "光影包启用"
                : !MaidHaloConfig.enableEndHaloSecondPhasePostFx() ? "本模组配置已关闭"
                : !specialHaloEffectEnabled() ? "RevelationFix 的 enableSpecialHaloEffect=false"
                : odamaneType == null || postEffectTrigger == null || odamaneShaderId == null ? "反射句柄缺失"
                : !postEffectTargetReady() ? "odamane 后处理 target 未就绪（后处理链未加载）"
                : "unknown");

        // 降级：energySwirl 主层
        halo.render(poseStack, buffer.getBuffer(swirlType), FULL_BRIGHT, OverlayTexture.NO_OVERLAY);

        // 玩家侧光影包分支：主层之后再叠一层 eyesAlpha（二阶段 alpha 固定 2.0）
        if (shaderPack && eyesPulseType != null) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 2.0F);
            try {
                halo.render(poseStack, bufferSource.getBuffer(eyesPulseType), FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                bufferSource.endBatch(eyesPulseType);
            } finally {
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            }
        }
    }

    /** 分支切换时打一条日志（每帧只多一次 int 比较），便于定位「看起来没生效」的原因。 */
    private static void logBranch(boolean postFx, String reason) {
        int key = postFx ? 1 : 0;
        if (key == loggedBranch) {
            return;
        }
        loggedBranch = key;
        if (postFx) {
            LOGGER.debug("[maid_halo_compat] 半血分支 = odamane 后处理");
        } else {
            LOGGER.debug("[maid_halo_compat] 半血分支 = 降级 energySwirl，原因: {}", reason);
        }
    }

    public static void render(PoseStack poseStack, MultiBufferSource buffer, LivingEntity entity) {
        if (!initialized) {
            init();
        }
        if (!available) {
            return;
        }

        // 与原实现同源：UUID 字符串长度 + 真实时间。
        // UUID.toString() 恒为 36 个字符，所以原式等价于 cos(36)；直接写常量，省掉每帧一次 String 分配。
        float zRot = (Mth.cos(36.0F) * 3201123.0F
                + (float) Blaze3D.getTime() * 90.0F) * (float) (Math.PI / 180.0);

        halo.translateAndRotate(poseStack);
        float lift = effectiveEndHaloLift(modelIdOf(entity));
        if (lift != 0.0F) {
            // 高度来源顺序：配置里按模型覆盖 → 酒狐内置 +0.5 → 全局 endHaloLiftDefault
            poseStack.translate(0.0D, -lift, 0.0D);
        }
        poseStack.translate(0.0D, 0.0D, -0.5D);

        bone2.zRot = zRot;
        bone3.zRot = zRot;
        bone4.zRot = -zRot;
        bone5.zRot = -zRot;
        bone.y = Mth.cos(zRot) + 1.0F;

        // 首阶段：vanilla 通道（entityCutoutNoCull），与玩家一致。
        // 二阶段（血量 < 50%）：交给 renderSecondPhase 复刻玩家侧后处理特效。
        boolean secondPhase = entity.getHealth() / entity.getMaxHealth() < 0.5F;
        if (!secondPhase) {
            halo.render(poseStack, buffer.getBuffer(cutoutType), FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            return;
        }
        renderSecondPhase(poseStack, buffer);

    }

    /** 取女仆当前模型 id（非女仆返回 null）。 */
    private static String modelIdOf(LivingEntity entity) {
        if (!(entity instanceof Mob mob)) {
            return null;
        }
        IMaid maid = IMaid.convert(mob);
        if (maid == null) {
            return null;
        }
        // YSM 模型：TLM 的 modelId 仍是 gecko/bedrock 的占位，真正的模型身份是 getYsmModelId()
        // （与 config/yes_steve_model/custom 下的文件夹名一致），配置页的 ysm 分组用的也是它。
        if (maid.isYsmModel()) {
            String ysmId = maid.getYsmModelId();
            if (ysmId != null && !ysmId.isBlank()) {
                return ysmId;
            }
        }
        return maid.getModelId();
    }

    /**
     * 该模型「未单独配置」时的默认高度：酒狐系列内置一个头（0.5），其余为 0，再叠加全局
     * {@code endHaloLiftDefault}。配置页的输入框初始值就用它。
     */
    public static float defaultEndHaloLift(String modelId) {
        float builtin = isWineFoxModelId(modelId) ? WINE_FOX_HEAD_LIFT : 0.0F;
        return builtin + MaidHaloConfig.endHaloLiftDefault();
    }

    /** 该模型最终生效的终末之环高度（模型空间方块，正值向上）。 */
    public static float effectiveEndHaloLift(String modelId) {
        Float override = MaidHaloConfig.endHaloLiftOverride(modelId);
        return override != null ? override : defaultEndHaloLift(modelId);
    }

    /**
     * 是否为酒狐（winefox）系列模型（带缓存，每帧只做一次 Map 查询）。
     *
     * <p>模型 id 形如 {@code geckolib:winefox} / {@code geckolib:winefox_hanfu}；
     * {@code winefox_little} / {@code winefox_mini} 是体型很矮的幼体/迷你版，不做内置补偿。</p>
     */
    private static boolean isWineFoxModelId(String modelId) {
        if (modelId == null || modelId.isEmpty()) {
            return false;
        }
        return WINE_FOX_CACHE.computeIfAbsent(modelId, id -> {
            int sep = id.indexOf(':');
            String path = sep >= 0 ? id.substring(sep + 1) : id;
            return path.startsWith("winefox") && !"winefox_little".equals(path) && !"winefox_mini".equals(path);
        });
    }
}
