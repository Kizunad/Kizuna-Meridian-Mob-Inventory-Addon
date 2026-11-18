package dev.kizuna.meridian.inventory;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.gamerules.GameRules;

final class InventoryDrops {
    private InventoryDrops() {
        // 掉落规则集中在一处，装备仍由原版独立处理。
    }

    static boolean shouldDrop(ServerLevel level, ItemStack stack) {
        // 尊重服主关闭生物掉落，以及原版消失诅咒的阻止掉落效果。
        return !stack.isEmpty() && level.getGameRules().get(GameRules.MOB_DROPS)
                && !EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP);
    }
}
