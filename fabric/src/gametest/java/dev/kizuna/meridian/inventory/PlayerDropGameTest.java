package dev.kizuna.meridian.inventory;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;

public final class PlayerDropGameTest {
    private static final int FAR_DISTANCE = 512;
    private static final int MERGE_TICKS = 40;
    private static final int NATURAL_COUNT = 5;
    private static final int DEATH_COUNT = 3;
    private static final int BONE_LIMIT = 24;
    private static final String SOURCE = "meridian_mob_inventory:player_drop";

    @GameTest
    public void naturalPickupDoesNotPreventDespawn(GameTestHelper context) {
        var mob = PickupTestSupport.skeleton(context);
        var observer = context.makeMockServerPlayerInLevel();
        observer.snapTo(mob.position());
        var ground = PickupTestSupport.ground(mob, new ItemStack(Items.BONE));
        PickupTestSupport.scan(context, mob);
        context.assertTrue(!ground.isAlive() && !mob.isPersistenceRequired(), "自然拾取意外设置了常驻");
        checkFromFarAway(context, mob);
        context.assertTrue(mob.isRemoved(), "只捡自然物品的骷髅没有被原版距离清除");
        context.succeed();
    }

    @GameTest
    public void existingPersistenceSurvivesNaturalPickup(GameTestHelper context) {
        var mob = PickupTestSupport.skeleton(context);
        mob.setCustomName(Component.literal("keeper"));
        mob.setPersistenceRequired();
        var ground = PickupTestSupport.ground(mob, new ItemStack(Items.BONE));
        PickupTestSupport.scan(context, mob);
        context.assertTrue(!ground.isAlive() && mob.isPersistenceRequired(), "自然拾取清除了原有的常驻");
        context.succeed();
    }

    @GameTest
    public void playerThrowOnlyProtectsAfterActualPickup(GameTestHelper context) {
        var mob = PickupTestSupport.skeleton(context);
        var player = playerBeside(context, mob);
        player.getInventory().setItem(0, new ItemStack(Items.BONE));
        player.getInventory().setSelectedSlot(0);
        player.drop(true /* all */);
        var item = InventoryTestSupport.drops(mob).stream().filter(drop -> drop.getItem().is(Items.BONE))
                .findFirst().orElseThrow();
        var stored = InventoryTestSupport.save(item);
        context.assertTrue(stored.getBooleanOr(SOURCE, false /* defaultValue */) && !stored.contains("Owner"),
                "玩家主动丢弃未写自有来源或冒用了 Owner");
        item.setPos(mob.position());
        item.setNoPickUpDelay();
        var bag = MobInventories.inventory(mob);
        bag.insertAt(0, new ItemStack(Items.BONE, BONE_LIMIT));
        PickupTestSupport.scan(context, mob);
        context.assertTrue(item.isAlive() && !mob.isPersistenceRequired(), "未真正捡进就设置了常驻");
        bag.extract(0, BONE_LIMIT);
        PickupTestSupport.scan(context, mob);
        context.assertTrue(!item.isAlive() && mob.isPersistenceRequired(), "玩家主动丢弃没有保护接收者");
        bag.extract(0, BONE_LIMIT);
        var loaded = InventoryTestSupport.reload(context, mob);
        checkFromFarAway(context, loaded);
        context.assertTrue(loaded.isAlive() && loaded.isPersistenceRequired(), "清空和读档后丢失常驻");
        context.succeed();
    }

    @GameTest
    public void deathDropsProtectAfterReload(GameTestHelper context) {
        var mob = PickupTestSupport.skeleton(context);
        var player = playerBeside(context, mob);
        var level = context.getLevel();
        var name = Component.literal(player.getStringUUID());
        dropOnDeath(level, player, name);
        var drops = InventoryTestSupport.drops(mob).stream()
                .filter(drop -> name.equals(drop.getItem().getCustomName()))
                .toList();
        var bones = drops.stream().filter(item -> item.getItem().is(Items.BONE)).findFirst().orElseThrow();
        var gear = drops.stream().filter(item -> item.getItem().is(Items.TOTEM_OF_UNDYING)).findFirst().orElseThrow();
        context.assertTrue(((GroundItemAccess) gear).meridian$isPlayerDrop(), "玩家死亡装备未标来源");
        level.getServer().getPlayerList().remove(player);
        var restored = reloadItem(context, bones);
        var saved = InventoryTestSupport.save(restored);
        context.assertTrue(saved.getBooleanOr(SOURCE, false /* defaultValue */) && !saved.contains("Thrower"),
                "无 Thrower 的死亡遗物重载后丢失来源");
        restored.setPos(mob.position());
        restored.setNoPickUpDelay();
        PickupTestSupport.scan(context, mob);
        context.assertTrue(!restored.isAlive() && mob.isPersistenceRequired(), "捡离线玩家遗物未设置常驻");
        var observer = playerBeside(context, mob);
        checkFromFarAway(context, mob);
        context.assertTrue(mob.isAlive() && observer.isAlive(), "捡遗物后被原版距离清除");
        context.succeed();
    }

    @GameTest
    public void naturalStackInheritsPlayerSource(GameTestHelper context) {
        var mob = PickupTestSupport.skeleton(context);
        var player = playerBeside(context, mob);
        var marked = player.drop(new ItemStack(Items.BONE), true /* thrownFromHand */);
        var natural = PickupTestSupport.ground(mob, new ItemStack(Items.BONE, NATURAL_COUNT));
        marked.setPos(natural.position());
        marked.setDeltaMovement(Vec3.ZERO);
        marked.setNoPickUpDelay();
        for (int tick = 0; tick < MERGE_TICKS && marked.isAlive(); tick++) {
            context.getLevel().tickNonPassenger(marked);
            context.getLevel().tickNonPassenger(natural);
        }
        context.assertTrue(natural.isAlive() && !marked.isAlive() && natural.getItem().getCount() == NATURAL_COUNT + 1,
                "未发生预期方向的原版合堆");
        var restored = reloadItem(context, natural);
        PickupTestSupport.scan(context, mob);
        context.assertTrue(!restored.isAlive() && mob.isPersistenceRequired(), "自然大堆合入玩家物品后丢了来源");
        context.succeed();
    }

    private static ServerPlayer playerBeside(GameTestHelper context, Skeleton mob) {
        var player = context.makeMockServerPlayerInLevel();
        player.snapTo(mob.position());
        return player;
    }

    private static void dropOnDeath(ServerLevel level, ServerPlayer player, Component name) {
        var bones = new ItemStack(Items.BONE, DEATH_COUNT);
        var totem = new ItemStack(Items.TOTEM_OF_UNDYING);
        bones.set(DataComponents.CUSTOM_NAME, name);
        totem.set(DataComponents.CUSTOM_NAME, name);
        player.getInventory().setItem(MobInventory.ROW_SIZE, bones);
        player.setItemSlot(EquipmentSlot.OFFHAND, totem);
        var rules = level.getGameRules();
        var server = level.getServer();
        boolean previous = rules.get(GameRules.KEEP_INVENTORY);
        try {
            rules.set(GameRules.KEEP_INVENTORY, false /* value */, server);
            player.setHealth(0);
            player.die(player.damageSources().genericKill());
        } finally {
            rules.set(GameRules.KEEP_INVENTORY, previous, server);
        }
    }

    private static ItemEntity reloadItem(GameTestHelper context, ItemEntity original) {
        var data = InventoryTestSupport.diskRoundTrip(InventoryTestSupport.save(original));
        original.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        var level = context.getLevel();
        var loaded = new ItemEntity(EntityTypes.ITEM, level);
        InventoryTestSupport.load(loaded, data);
        context.assertTrue(level.addFreshEntity(loaded), "掉落物未重新加入世界");
        return loaded;
    }

    private static void checkFromFarAway(GameTestHelper context, Skeleton mob) {
        Vec3 original = mob.position();
        mob.setPos(original.add(FAR_DISTANCE, 0, 0));
        var nearest = context.getLevel().getNearestPlayer(mob, -1);
        int despawnDistance = mob.getType().getCategory().getDespawnDistance();
        context.assertTrue(nearest != null && nearest.distanceTo(mob) > despawnDistance, "未建立原版距离清除条件");
        mob.checkDespawn();
        if (mob.isAlive()) {
            mob.setPos(original);
        }
    }
}
