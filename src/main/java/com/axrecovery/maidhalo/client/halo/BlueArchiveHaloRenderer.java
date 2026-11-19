package com.axrecovery.maidhalo.client.halo;

import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * BA光环包（Blue-Archive-Halos）的女仆侧绘制 —— **照搬玩家侧方案**。
 *
 * <p>玩家侧链路（原模组实现）：</p>
 * <ol>
 *   <li>光环是 {@code HaloItem extends ArmorItem(HELMET)} 且实现 GeckoLib 的 {@code GeoItem}；</li>
 *   <li>{@code HaloClientItem implements IClientItemExtensions} 的
 *       {@code getHumanoidArmorModel(entity, stack, HEAD, original)} 里按物品 NBT 的 {@code CharacterName}
 *       取/建 {@code HaloRenderer extends GeoArmorRenderer}（同一实例按名字缓存）；</li>
 *   <li>原版 {@code HumanoidArmorLayer} 调 {@code renderer.prepForRender(...)} 后按
 *       GeckoLib 的 {@code GeoRenderer} 管线（{@code getTextureLocation → getRenderType → defaultRender}）绘制，
 *       最后 {@code doPostRenderCleanup()}；</li>
 *   <li>绘制基座是**身体空间**（模型自带 {@code armorHead} 骨骼负责定位到头部）。</li>
 * </ol>
 *
 * <p>女仆没有护甲层，所以这里按同样的顺序反射复用同一个 {@code GeoArmorRenderer}：
 * 贴图、动画、{@code AutoGlowingGeoLayer} 辉光层全部来自原模组自身，与玩家侧同源。</p>
 */
public final class BlueArchiveHaloRenderer {
    private static final Logger LOGGER = LogManager.getLogger("maid_halo_compat");

    private static Method extensionsOf;          // IClientItemExtensions.of(ItemStack)
    private static Method getArmorModel;         // getHumanoidArmorModel(LivingEntity, ItemStack, EquipmentSlot, HumanoidModel)
    private static Method prepForRender;         // GeoArmorRenderer#prepForRender(Entity, ItemStack, EquipmentSlot, HumanoidModel)
    private static Method getTextureLocation;    // GeoRenderer#getTextureLocation(T)
    private static Method getRenderType;         // GeoRenderer#getRenderType(T, ResourceLocation, MultiBufferSource, float)
    private static Method defaultRender;         // GeoRenderer#defaultRender(PoseStack, T, MultiBufferSource, RenderType, VertexConsumer, float, float, int)
    private static Method doPostRenderCleanup;   // GeoArmorRenderer#doPostRenderCleanup()
    private static boolean initialized;
    private static boolean available;
    private static boolean failureLogged;

    private BlueArchiveHaloRenderer() {
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
        if (!CompatMods.isLoaded(CompatMods.BLUE_ARCHIVE)) {
            available = false;
            LOGGER.debug("未检测到 minecraftbluearchivehalo，跳过BA光环适配");
            return;
        }
        try {
            ClassLoader loader = BlueArchiveHaloRenderer.class.getClassLoader();

            Class<?> extensions = Class.forName(
                    "net.minecraftforge.client.extensions.common.IClientItemExtensions", false, loader);
            extensionsOf = extensions.getMethod("of", ItemStack.class);
            getArmorModel = extensions.getMethod("getHumanoidArmorModel",
                    LivingEntity.class, ItemStack.class, EquipmentSlot.class, HumanoidModel.class);

            Class<?> animatable = Class.forName(
                    "software.bernie.geckolib.core.animatable.GeoAnimatable", false, loader);
            Class<?> geoRenderer = Class.forName(
                    "software.bernie.geckolib.renderer.GeoRenderer", false, loader);
            getTextureLocation = geoRenderer.getMethod("getTextureLocation", animatable);
            getRenderType = geoRenderer.getMethod("getRenderType",
                    animatable, net.minecraft.resources.ResourceLocation.class, MultiBufferSource.class, float.class);
            defaultRender = geoRenderer.getMethod("defaultRender",
                    PoseStack.class, animatable, MultiBufferSource.class, RenderType.class,
                    com.mojang.blaze3d.vertex.VertexConsumer.class, float.class, float.class, int.class);

            Class<?> armorRenderer = Class.forName(
                    "software.bernie.geckolib.renderer.GeoArmorRenderer", false, loader);
            prepForRender = armorRenderer.getMethod("prepForRender",
                    net.minecraft.world.entity.Entity.class, ItemStack.class, EquipmentSlot.class, HumanoidModel.class);
            doPostRenderCleanup = armorRenderer.getMethod("doPostRenderCleanup");

            available = true;
            LOGGER.debug("已接入BA光环（照搬玩家侧 GeoArmorRenderer 方案）");
        } catch (Throwable t) {
            available = false;
            extensionsOf = null;
            getArmorModel = null;
            prepForRender = null;
            getTextureLocation = null;
            getRenderType = null;
            defaultRender = null;
            doPostRenderCleanup = null;
            LOGGER.warn("无法接入BA光环渲染器，女仆将不渲染该光环：{}", t.toString());
        }
    }

    /**
     * GeckoLib 护甲渲染器用 baseModel 拷贝「头部位置/旋转」（{@code applyBaseTransformations}）。
     *
     * <p>玩家侧传的是 {@code PlayerModel}，于是光环会跟随玩家头部（包括玩家视角），
     * 女仆侧如果照抄就会出现「光环主动指向玩家」。这里改用一个**中性人形模型**
     * （头骨在原点、旋转为 0），光环就固定贴在女仆头骨坐标系上、不再跟着玩家转头。</p>
     */
    private static HumanoidModel<?> neutralBase;

    private static HumanoidModel<?> humanoidBase() {
        if (neutralBase == null) {
            neutralBase = createNeutralBase();
        }
        return neutralBase;
    }

    private static HumanoidModel<?> createNeutralBase() {
        net.minecraft.client.model.geom.builders.MeshDefinition mesh =
                new net.minecraft.client.model.geom.builders.MeshDefinition();
        net.minecraft.client.model.geom.builders.PartDefinition root = mesh.getRoot();
        for (String part : new String[]{"head", "hat", "body", "right_arm", "left_arm", "right_leg", "left_leg"}) {
            root.addOrReplaceChild(part,
                    net.minecraft.client.model.geom.builders.CubeListBuilder.create(),
                    net.minecraft.client.model.geom.PartPose.ZERO);
        }
        return new HumanoidModel<>(net.minecraft.client.model.geom.builders.LayerDefinition
                .create(mesh, 64, 64).bakeRoot());
    }

    public static void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                              LivingEntity entity, ItemStack stack) {
        if (!initialized) {
            init();
        }
        if (!available || stack == null || stack.isEmpty()) {
            return;
        }
        HumanoidModel<?> base = humanoidBase();
        if (base == null) {
            return;
        }
        float partialTick = Minecraft.getInstance().getFrameTime();
        try {
            Object extensions = extensionsOf.invoke(null, stack);
            Object armorModel = getArmorModel.invoke(extensions, entity, stack, EquipmentSlot.HEAD, base);
            if (armorModel == null) {
                return;
            }
            // 与原版 HumanoidArmorLayer 完全相同的顺序
            prepForRender.invoke(armorModel, entity, stack, EquipmentSlot.HEAD, base);

            Object animatable = stack.getItem();
            Object texture = getTextureLocation.invoke(armorModel, animatable);
            Object renderType = getRenderType.invoke(armorModel, animatable, texture, buffer, partialTick);
            Object consumer = buffer.getBuffer((RenderType) renderType);
            defaultRender.invoke(armorModel, poseStack, animatable, buffer, renderType, consumer,
                    partialTick, 1.0F, packedLight);
            doPostRenderCleanup.invoke(armorModel);
        } catch (Throwable t) {
            if (!failureLogged) {
                failureLogged = true;
                LOGGER.warn("BA光环渲染调用失败（之后不再重复记录）：{}", t.toString());
            }
        }
    }
}
