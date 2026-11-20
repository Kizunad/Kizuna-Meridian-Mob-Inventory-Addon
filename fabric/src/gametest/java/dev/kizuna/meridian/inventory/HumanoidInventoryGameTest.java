package dev.kizuna.meridian.inventory;

import dev.kizuna.meridian.inventory.test.DeathLootInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;

public final class HumanoidInventoryGameTest {
    private static final String MORTAL_TIER_TAG = "meridian_humanoid.tier.mortal";
    private static final int VILLAGER_SLOTS = 8;
    private static final int PILLAGER_SLOTS = 5;

    @GameTest
    public void villagerKeepsVanillaInventory(GameTestHelper context) {
        checkPack(context, EntityTypes.VILLAGER, VILLAGER_SLOTS);
    }

    @GameTest
    public void traderKeepsVanillaInventory(GameTestHelper context) {
        checkPack(context, EntityTypes.WANDERING_TRADER, VILLAGER_SLOTS);
    }

    @GameTest
    public void pillagerKeepsVanillaInventory(GameTestHelper context) {
        checkPack(context, EntityTypes.PILLAGER, PILLAGER_SLOTS);
    }

    @GameTest
    public void vindicatorKeepsVanillaInventory(GameTestHelper context) {
        checkPack(context, EntityTypes.VINDICATOR, 0);
    }

    @GameTest
    public void evokerKeepsVanillaInventory(GameTestHelper context) {
        checkPack(context, EntityTypes.EVOKER, 0);
    }

    @GameTest
    public void witchKeepsVanillaInventory(GameTestHelper context) {
        checkPack(context, EntityTypes.WITCH, 0);
    }

    @GameTest
    public void illusionerKeepsVanillaInventory(GameTestHelper context) {
        checkPack(context, EntityTypes.ILLUSIONER, 0);
    }

    @GameTest
    public void zombieKeepsVanillaInventory(GameTestHelper context) {
        checkPack(context, EntityTypes.ZOMBIE, 0);
    }

    @GameTest
    public void huskKeepsVanillaInventory(GameTestHelper context) {
        checkPack(context, EntityTypes.HUSK, 0);
    }

    @GameTest
    public void drownedKeepsVanillaInventory(GameTestHelper context) {
        checkPack(context, EntityTypes.DROWNED, 0);
    }

    @GameTest
    public void zombieVillagerKeepsVanillaInventory(GameTestHelper context) {
        checkPack(context, EntityTypes.ZOMBIE_VILLAGER, 0);
    }

    private static void checkPack(GameTestHelper context, EntityType<? extends Mob> type, int vanillaSize) {
        var level = context.getLevel();
        Mob mob = type.create(level, EntitySpawnReason.LOAD);
        checkStorage(context, mob, vanillaSize);
        if (mob instanceof Zombie) {
            Mob baby = type.create(level, EntitySpawnReason.LOAD);
            baby.setBaby(true /* baby */);
            context.assertTrue(baby.isBaby(), "幼体夹具未生效");
            checkStorage(context, baby, vanillaSize);
        }
        context.succeed();
    }

    private static void checkStorage(GameTestHelper context, Mob mob, int vanillaSize) {
        mob.setPos(Vec3.atBottomCenterOf(context.absolutePos(InventoryTestSupport.SPAWN)));
        var bread = new ItemStack(Items.BREAD);
        if (mob instanceof InventoryCarrier carrier) {
            var vanilla = carrier.getInventory();
            context.assertTrue(vanillaSize == vanilla.getContainerSize(), "原版库存大小不符");
            vanilla.setItem(0, bread);
        } else {
            context.assertTrue(vanillaSize == 0, "缺少原版库存");
        }
        context.assertTrue(context.getLevel().addFreshEntity(mob), "实体未进入世界");
        var inventory = MobInventories.inventory(mob);
        context.assertTrue(inventory.snapshot().size() == MobInventory.SIZE, "没有两行背囊");
        InventoryTestSupport.assertEmpty(context, inventory);
        inventory.insertAt(0, new ItemStack(Items.EMERALD));
        if (mob instanceof InventoryCarrier carrier) {
            context.assertTrue(carrier.getInventory().getItem(0) == bread, "背囊写入覆盖了原版库存");
        }
        context.assertTrue(mob.getGoalSelector().getAvailableGoals().stream()
                .noneMatch(goal -> goal.getGoal() instanceof PickupGoal), "类人安装了骷髅寻物目标");
        mob.discard();
    }

    @GameTest
    public void zeroHandChanceDiscardsBirthBowAndDropsOrdinaryBow(GameTestHelper context) {
        var mob = carrier(context);
        var pack = MobInventories.inventory(mob);
        pack.insertBirthWeapon(new ItemStack(Items.BOW));
        pack.swap(0, MobInventory.ROW_SIZE);
        var ordinary = new ItemStack(Items.BOW);
        ordinary.set(DataComponents.CUSTOM_NAME, Component.literal("拾来的弓"));
        var expected = ordinary.copy();
        pack.insertAt(0, ordinary);
        var saved = InventoryTestSupport.diskRoundTrip(InventoryTestSupport.save(mob));
        mob.discard();
        var loaded = EntityTypes.VINDICATOR.create(context.getLevel(), EntitySpawnReason.LOAD);
        InventoryTestSupport.load(loaded, saved);
        context.assertTrue(context.getLevel().addFreshEntity(loaded), "死亡夹具未重载");
        loaded.setDropChance(EquipmentSlot.MAINHAND, 0);
        loaded.die(loaded.damageSources().genericKill());
        repeatLoot(context, loaded);
        context.assertTrue(drops(loaded, Items.BOW) == 1, "主手零掉率时只应掉后来放入的那把弓");
        boolean expectedDrop = InventoryTestSupport.drops(loaded).stream()
                .anyMatch(item -> ItemStack.matches(expected, item.getItem()));
        context.assertTrue(expectedDrop, "换格后出生兵器标记落到了普通弓上");
        InventoryTestSupport.assertEmpty(context, MobInventories.inventory(loaded));
        context.succeed();
    }

    @GameTest
    public void extractedWeaponDoesNotMarkItsReplacement(GameTestHelper context) {
        var mob = carrier(context);
        var inventory = MobInventories.inventory(mob);
        inventory.insertBirthWeapon(new ItemStack(Items.BOW));
        var removed = inventory.exchange(0, new ItemStack(Items.CROSSBOW));
        context.assertTrue(removed.is(Items.BOW), "缴械没有取出原武器");
        mob.setDropChance(EquipmentSlot.MAINHAND, 0);
        mob.die(mob.damageSources().genericKill());
        boolean droppedExpectedItems = drops(mob, Items.BOW) == 0 && drops(mob, Items.CROSSBOW) == 1;
        context.assertTrue(droppedExpectedItems, "替换物继承了出生掉率");
        context.succeed();
    }

    @GameTest
    public void guaranteedBirthWeaponAndHandEachDropOnce(GameTestHelper context) {
        var mob = carrier(context);
        var inventory = MobInventories.inventory(mob);
        inventory.insertBirthWeapon(new ItemStack(Items.BOW));
        mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        mob.setGuaranteedDrop(EquipmentSlot.MAINHAND);
        mob.die(mob.damageSources().genericKill());
        repeatLoot(context, mob);
        boolean droppedExpectedItems = drops(mob, Items.BOW) == 1 && drops(mob, Items.IRON_AXE) == 1;
        context.assertTrue(droppedExpectedItems, "出生兵器或手持掉落数有误");
        context.succeed();
    }

    @GameTest
    public void disabledDropsConsumeGuaranteedBirthEquipment(GameTestHelper context) {
        var level = context.getLevel();
        var rules = level.getGameRules();
        var original = rules.get(GameRules.MOB_DROPS);
        var mob = carrier(context);
        var pack = MobInventories.inventory(mob);
        pack.insertBirthWeapon(new ItemStack(Items.BOW));
        pack.insertAt(1, new ItemStack(Items.EMERALD));
        mob.setGuaranteedDrop(EquipmentSlot.MAINHAND);
        try {
            rules.set(GameRules.MOB_DROPS, false /* value */, level.getServer());
            mob.die(mob.damageSources().genericKill());
        } finally {
            rules.set(GameRules.MOB_DROPS, original, level.getServer());
        }
        repeatLoot(context, mob);
        InventoryTestSupport.assertEmpty(context, pack);
        boolean noPackDrops = drops(mob, Items.BOW) == 0 && drops(mob, Items.EMERALD) == 0;
        context.assertTrue(noPackDrops, "禁掉落后仍掉出背囊物品");
        context.succeed();
    }

    private static Mob carrier(GameTestHelper context) {
        var level = context.getLevel();
        Mob mob = EntityTypes.VINDICATOR.create(level, EntitySpawnReason.LOAD);
        mob.addTag(MORTAL_TIER_TAG);
        mob.setPos(Vec3.atBottomCenterOf(context.absolutePos(InventoryTestSupport.SPAWN)));
        context.assertTrue(level.addFreshEntity(mob), "背囊测试实体未进入世界");
        return mob;
    }

    private static void repeatLoot(GameTestHelper context, Mob mob) {
        ((DeathLootInvoker) mob).inventoryTest$repeatDeathLoot(context.getLevel(), mob.damageSources().genericKill());
    }

    private static int drops(Mob mob, Item type) {
        return InventoryTestSupport.drops(mob).stream().map(ItemEntity::getItem)
                .filter(stack -> stack.is(type)).mapToInt(ItemStack::getCount).sum();
    }
}
