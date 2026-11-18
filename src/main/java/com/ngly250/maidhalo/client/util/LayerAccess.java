package com.ngly250.maidhalo.client.util;

import java.lang.reflect.Method;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 给 {@link LivingEntityRenderer} 追加渲染层的最小侵入式访问器。
 *
 * <p>TLM 的扩展 API {@code ILittleMaid#addAdditionMaidLayer(EntityMaidRenderer, Context)} 把
 * 女仆渲染器直接交给第三方模组，但 {@code LivingEntityRenderer#addLayer(RenderLayer)} 是
 * {@code protected final}，TLM 并没有为扩展方暴露公开的 addLayer 入口。</p>
 *
 * <p>这里刻意不使用 Mixin，也不使用 AccessTransformer（AT 会全局放宽原版类可见性，
 * 并且需要额外的构建配置）；而是按「参数为 RenderLayer、返回 boolean」的签名在
 * {@link LivingEntityRenderer} 上查找唯一目标方法并缓存。
 * 该查找与映射无关：开发环境方法名为 {@code addLayer}，生产环境为 SRG 名 {@code m_115326_}，
 * 两种情况下都能命中同一个方法。</p>
 *
 * <p>这是一处“跨模组兼容的临时手段”，只影响我们自己挂上去的那一个渲染层。</p>
 */
public final class LayerAccess {
    private static final Logger LOGGER = LogManager.getLogger("maid_halo_compat");

    private static Method addLayerMethod;
    private static boolean lookedUp;

    private LayerAccess() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void addLayer(LivingEntityRenderer<?, ?> renderer, RenderLayer<?, ?> layer) {
        Method method = findAddLayer();
        if (method == null) {
            LOGGER.error("无法在 LivingEntityRenderer 上定位 addLayer(RenderLayer) 方法，女仆光环层未挂载");
            return;
        }
        try {
            method.invoke(renderer, layer);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.error("给 {} 挂载渲染层失败", renderer.getClass().getName(), e);
        }
    }

    private static Method findAddLayer() {
        if (lookedUp) {
            return addLayerMethod;
        }
        lookedUp = true;
        for (Method candidate : LivingEntityRenderer.class.getDeclaredMethods()) {
            Class<?>[] parameters = candidate.getParameterTypes();
            if (parameters.length != 1 || !RenderLayer.class.isAssignableFrom(parameters[0])) {
                continue;
            }
            if (candidate.getReturnType() != boolean.class) {
                continue;
            }
            try {
                candidate.setAccessible(true);
            } catch (RuntimeException e) {
                LOGGER.error("无法访问 LivingEntityRenderer#{}", candidate.getName(), e);
                continue;
            }
            addLayerMethod = candidate;
            break;
        }
        if (addLayerMethod == null) {
            LOGGER.error("LivingEntityRenderer 上不存在符合签名 (RenderLayer)Z 的方法");
        }
        return addLayerMethod;
    }
}
