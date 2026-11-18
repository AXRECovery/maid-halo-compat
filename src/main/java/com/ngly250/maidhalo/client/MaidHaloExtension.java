package com.ngly250.maidhalo.client;

import com.ngly250.maidhalo.client.layer.MaidHaloGeoLayer;
import com.ngly250.maidhalo.client.layer.MaidHaloRenderLayer;
import com.ngly250.maidhalo.client.util.LayerAccess;
import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.EntityMaidRenderer;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.GeckoEntityMaidRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * TLM 官方扩展入口。
 *
 * <p>{@link LittleMaidExtension} 会被 TLM 的 {@code AnnotatedInstanceUtil} 通过 Forge
 * 的注解扫描发现（{@code TouhouLittleMaid.EXTENSIONS}），无需 ServiceLoader 或手动注册。
 * 因此本类必须保留 public 无参构造。</p>
 */
@LittleMaidExtension
public class MaidHaloExtension implements ILittleMaid {
    private static final Logger LOGGER = LogManager.getLogger("maid_halo_compat");

    public MaidHaloExtension() {
        // TLM 的 AnnotatedInstanceUtil 要求 public 无参构造；这里顺手留一条可用于验证的日志。
        LOGGER.debug("[maid_halo_compat] MaidHaloExtension 已实例化（TLM 扩展入口就绪）");
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void addAdditionMaidLayer(EntityMaidRenderer renderer, EntityRendererProvider.Context context) {
        try {
            LayerAccess.addLayer(renderer, new MaidHaloRenderLayer(renderer));
        } catch (Throwable t) {
            LOGGER.error("给女仆（传统 Bedrock 模型）挂载光环渲染层失败", t);
        }
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void addAdditionGeckoMaidLayer(GeckoEntityMaidRenderer<? extends Mob> renderer,
                                          EntityRendererProvider.Context context) {
        try {
            GeoLayerRendererHolder.attach(renderer);
        } catch (Throwable t) {
            LOGGER.error("给女仆（GeckoLib 模型）挂载光环渲染层失败", t);
        }
    }

    /** 单独一层，避免泛型通配符污染主类。 */
    private static final class GeoLayerRendererHolder {
        @SuppressWarnings({"unchecked", "rawtypes"})
        private static void attach(GeckoEntityMaidRenderer<? extends Mob> renderer) {
            var layer = new MaidHaloGeoLayer((com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.IGeoEntityRenderer) renderer);
            renderer.addGeoLayerRenderer(layer);
        }
    }
}
