package dev.kizuna.meridian.inventory;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.gamerules.GameRules;

final class InventoryDrops {
    private static final int DROP_DAMAGE_MARGIN = 3;

    private InventoryDrops() {
        // 掉落规则集中在一处，装备仍由原版独立处理。
    }

    static boolean shouldDrop(ServerLevel level, ItemStack stack) {
        // 尊重服主关闭生物掉落，以及原版消失诅咒的阻止掉落效果。
        return !stack.isEmpty() && level.getGameRules().get(GameRules.MOB_DROPS)
                && !EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP);
    }

    static void dropAsMainHand(ServerLevel level, Mob mob, DamageSource source, ItemStack weapon) {
        var chances = mob.getDropChances();
        float baseChance = chances.byEquipment(EquipmentSlot.MAINHAND);
        boolean preserved = chances.isPreserved(EquipmentSlot.MAINHAND);
        if (baseChance == 0 || !shouldDrop(level, weapon) || !preserved && mob.getLastHurtByPlayerMemoryTime() <= 0) {
            return;
        }
        float chance;
        if (source.getEntity() instanceof LivingEntity killer) {
            chance = EnchantmentHelper.processEquipmentDropChance(level, killer, source, baseChance);
        } else {
            chance = baseChance;
        }
        var random = mob.getRandom();
        if (random.nextFloat() >= chance) {
            return;
        }
        if (!preserved && weapon.isDamageableItem()) {
            int maxDamage = weapon.getMaxDamage();
            int remaining = 1 + random.nextInt(Math.max(maxDamage - DROP_DAMAGE_MARGIN, 1));
            weapon.setDamageValue(maxDamage - random.nextInt(remaining));
        }
        mob.spawnAtLocation(level, weapon);
    }
}
