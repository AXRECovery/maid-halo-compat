package com.axrecovery.maidhalo.client.halo;

import net.minecraft.core.NonNullList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.ModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

/**
 * 从女仆的 Curios 饰品栏里找出已佩戴的光环。
 *
 * <p>遍历方式与 Curios 自身的 {@code CuriosLayer} 保持一致（先 cosmetic 槽、再 functional 槽，
 * 并尊重每个槽位的「渲染开关」），从而让女仆的判定结果与玩家完全同源。</p>
 *
 * <p>注意：TLM 的女仆在 {@code data/touhou_little_maid/curios/entities/curios.json} 里声明的
 * {@code head} 是 <b>原生 Curios 头饰槽</b>（不是 TLM 自造槽位），所以这里读到的就是
 * Goety 光环物品本体，不存在「拓展头饰槽位」导致的额外风险。</p>
 *
 * <p>所有直接引用 Curios 类型的代码都放在内部类 {@link CuriosSupport} 里：Curios 没装时
 * 外层类仍可安全加载，只是返回 {@link HaloKind#NONE}。</p>
 */
public final class HaloSources {

    /** 命中的光环：类型 + 具体物品栈（终末之环/不洁圣冠的渲染需要知道是哪一个物品，才能复刻原版贴图选择）。 */
    public record EquippedHalo(HaloKind kind, ItemStack stack) {
        public static final EquippedHalo NONE = new EquippedHalo(HaloKind.NONE, ItemStack.EMPTY);
    }

    /**
     * 可用性标记放在外层类：Curios 缺失时连 {@link CuriosSupport} 都不会被加载/校验，
     * 从而彻底避免软依赖缺失时在类校验阶段触发 {@code NoClassDefFoundError}。
     */
    private static final boolean CURIOS_LOADED = ModList.get().isLoaded("curios");

    /**
     * 「每个游戏刻最多扫一次」的缓存。
     *
     * <p>渲染频率远高于 tick（60fps ≈ 每秒 60 次渲染、20 次 tick），而 Curios 槽位内容只会随 tick 变化，
     * 所以同一 tick 内重复扫描纯属浪费。这里按实体 UUID + {@code tickCount} 缓存，跨 tick 自动失效，
     * 视觉上最多延迟 1 tick（≈16ms），不会改变任何既有表现。</p>
     */
    private static final Map<UUID, CachedHalo> PER_TICK = new ConcurrentHashMap<>();
    private static final int CACHE_LIMIT = 512;

    private record CachedHalo(int tickCount, EquippedHalo halo) {
    }

    private HaloSources() {
    }

    public static EquippedHalo findEquippedHalo(LivingEntity entity) {
        if (entity == null) {
            return EquippedHalo.NONE;
        }
        // 头盔槽优先：BA光环是护甲物品（HaloItem extends ArmorItem(HELMET)），不经过 Curios
        EquippedHalo helmet = helmetHalo(entity);
        if (helmet.kind() != HaloKind.NONE) {
            return helmet;
        }
        if (!CURIOS_LOADED) {
            return EquippedHalo.NONE;
        }
        UUID id = entity.getUUID();
        int tick = entity.tickCount;
        CachedHalo cached = PER_TICK.get(id);
        if (cached != null && cached.tickCount() == tick) {
            return cached.halo();
        }
        EquippedHalo found = CuriosSupport.find(entity);
        if (PER_TICK.size() > CACHE_LIMIT) {
            PER_TICK.clear();
        }
        PER_TICK.put(id, new CachedHalo(tick, found));
        return found;
    }


    /** 头盔槽里的光环（如BA光环包）。 */
    private static EquippedHalo helmetHalo(LivingEntity entity) {
        ItemStack stack = entity.getItemBySlot(EquipmentSlot.HEAD);
        if (stack.isEmpty()) {
            return EquippedHalo.NONE;
        }
        HaloKind kind = HaloKind.of(stack);
        return kind == HaloKind.NONE ? EquippedHalo.NONE : new EquippedHalo(kind, stack);
    }

    /** 真正接触 Curios API 的部分（独立类，避免软依赖缺失时被提前解析）。 */
    private static final class CuriosSupport {
        private static final Logger LOGGER = LogManager.getLogger("maid_halo_compat");
        private static boolean loggedFailure;

        static EquippedHalo find(LivingEntity entity) {
            LazyOptional<ICuriosItemHandler> optional = CuriosApi.getCuriosInventory(entity);
            if (!optional.isPresent()) {
                return EquippedHalo.NONE;
            }
            ICuriosItemHandler handler;
            try {
                handler = optional.orElse(null);
            } catch (RuntimeException e) {
                if (!loggedFailure) {
                    loggedFailure = true;
                    LOGGER.warn("[maid_halo_compat] 读取女仆 Curios 数据失败，本次不渲染光环", e);
                }
                return EquippedHalo.NONE;
            }
            if (handler == null) {
                return EquippedHalo.NONE;
            }

            for (ICurioStacksHandler stacksHandler : handler.getCurios().values()) {
                EquippedHalo found = scanHandler(stacksHandler);
                if (found.kind() != HaloKind.NONE) {
                    return found;
                }
            }
            return EquippedHalo.NONE;
        }

        private static EquippedHalo scanHandler(ICurioStacksHandler stacksHandler) {
            IDynamicStackHandler functional = stacksHandler.getStacks();
            IDynamicStackHandler cosmetic = stacksHandler.getCosmeticStacks();
            NonNullList<Boolean> renders = stacksHandler.getRenders();
            int slots = Math.max(functional.getSlots(), cosmetic.getSlots());

            for (int i = 0; i < slots; i++) {
                boolean renderable = renders != null && renders.size() > i && Boolean.TRUE.equals(renders.get(i));

                // 与 CuriosLayer 相同：cosmetic 槽优先，为空且允许渲染时退回到本体槽
                ItemStack stack = i < cosmetic.getSlots() ? cosmetic.getStackInSlot(i) : ItemStack.EMPTY;
                if (stack.isEmpty() && renderable && i < functional.getSlots()) {
                    stack = functional.getStackInSlot(i);
                }
                if (stack.isEmpty()) {
                    continue;
                }
                HaloKind kind = HaloKind.of(stack);
                if (kind != HaloKind.NONE) {
                    return new EquippedHalo(kind, stack);
                }
            }
            return EquippedHalo.NONE;
        }
    }
}
