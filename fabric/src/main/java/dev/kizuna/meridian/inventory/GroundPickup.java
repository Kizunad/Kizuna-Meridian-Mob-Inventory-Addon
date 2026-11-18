package dev.kizuna.meridian.inventory;

import dev.kizuna.bs3d.weights.protocol.pb.WeightsProtocol.Status;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;

final class GroundPickup {
    private GroundPickup() {
    }

    static boolean allowed(ServerLevel level, Mob mob) {
        return mob.isAlive() && mob.getTarget() == null && level.getGameRules().get(GameRules.MOB_GRIEFING)
                && MobInventories.state(mob).available();
    }

    static boolean available(ItemEntity item) {
        return item.isAlive() && !item.hasPickUpDelay() && !item.getItem().isEmpty()
                && !((GroundItemAccess) item).meridian$reserved();
    }

    static int quantity(ServerLevel level, Mob mob, ItemEntity item) {
        if (!allowed(level, mob) || !available(item)) {
            return 0;
        }
        MobInventory inventory = MobInventories.inventory(mob);
        List<ItemStack> slots = inventory.snapshot();
        ItemStack offered = item.getItem();
        int limit = MobInventoryFabric.WANTED.allowance(mob, slots, offered);
        if (limit == 0) {
            return 0;
        }
        var current = MobBurden.measure(level, mob, slots);
        if (current.getStatus() != Status.OK || current.getEffectiveGrams() >= MobBurden.PICKUP_GRAMS) {
            return 0;
        }
        var full = inventory.previewInsertion(offered.copyWithCount(limit));
        int capacity = limit - full.remainder().getCount();
        if (capacity == 0 || fits(level, mob, full.slots())) {
            return capacity;
        }
        // 插入按行顺序填充，数量增加不会降低任一来源的重量。
        int low = 0;
        int high = capacity - 1;
        while (low < high) {
            int count = low + (high - low + 1) / 2;
            var preview = inventory.previewInsertion(offered.copyWithCount(count));
            if (fits(level, mob, preview.slots())) {
                low = count;
            } else {
                high = count - 1;
            }
        }
        return low;
    }

    private static boolean fits(ServerLevel level, Mob mob, List<ItemStack> slots) {
        var weight = MobBurden.measure(level, mob, slots);
        return weight.getStatus() == Status.OK && weight.getEffectiveGrams() <= MobBurden.PICKUP_GRAMS;
    }

    static void collect(ServerLevel level, Mob mob, ItemEntity item) {
        // 同一服务器线程上重读地面实物、两行库存与装备；寻路时选中的数量可能已失效。
        int count = quantity(level, mob, item);
        if (count == 0) {
            return;
        }
        ItemStack ground = item.getItem();
        int inserted = MobInventories.inventory(mob).insert(ground.copyWithCount(count));
        if (inserted == 0) {
            return;
        }
        if (((GroundItemAccess) item).meridian$isPlayerDrop()) {
            mob.setPersistenceRequired();
        }
        mob.onItemPickup(item);
        mob.take(item, inserted);
        if (inserted == ground.getCount()) {
            item.discard();
        } else {
            item.setItem(ground.copyWithCount(ground.getCount() - inserted));
        }
    }
}
