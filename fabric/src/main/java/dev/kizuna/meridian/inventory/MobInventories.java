package dev.kizuna.meridian.inventory;

import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;

/** 已登记实体的权威库存入口。 */
public final class MobInventories {
    private static final Set<EntityType<?>> TYPES = Set.of(EntityTypes.SKELETON, EntityTypes.VILLAGER,
            EntityTypes.WANDERING_TRADER, EntityTypes.PILLAGER, EntityTypes.VINDICATOR, EntityTypes.EVOKER,
            EntityTypes.WITCH, EntityTypes.ILLUSIONER, EntityTypes.ZOMBIE, EntityTypes.HUSK, EntityTypes.DROWNED,
            EntityTypes.ZOMBIE_VILLAGER);

    private MobInventories() {
        // 查询入口没有全局实体缓存，生命周期随实体本身。
    }

    public static boolean supports(Entity entity) {
        return TYPES.contains(entity.getType());
    }

    public static boolean usesSkeletonSupplies(Entity entity) {
        return entity.getType() == EntityTypes.SKELETON;
    }

    public static MobInventory inventory(Mob mob) {
        // 所有外部库存访问都校验服务器线程与实体登记。
        return state(mob).inventory();
    }

    public static InventoryState state(Mob mob) {
        // 客户端没有第二份可写库存，也不允许异步线程修改物品事实。
        if (!(mob.level() instanceof ServerLevel level) || !level.getServer().isSameThread()) {
            throw new IllegalStateException("生物背囊只能在服务器线程访问");
        }
        if (!supports(mob)) {
            throw new IllegalArgumentException("实体未登记背囊：" + mob.getType());
        }
        return ((InventoryHolder) mob).meridianInventoryState();
    }
}
