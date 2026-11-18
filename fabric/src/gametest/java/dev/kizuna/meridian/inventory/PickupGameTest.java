package dev.kizuna.meridian.inventory;

import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;

public final class PickupGameTest {
    private static final int BONE_LIMIT = 24;
    private static final int SURPLUS = 6;
    private static final int SCAN = PickupTestSupport.SCAN_WINDOW;
    private static final int STACK = PickupTestSupport.STACK;
    private static final int TARGET_OFFSET = 4;
    private static final int OVERRIDE_LIMIT = 3;
    private static final Identifier OVERLAP = Identifier.fromNamespaceAndPath(
            "meridian_mob_inventory_test", "wanted_items/overlap.json");

    @GameTest
    public void dataPackOverrideCountsOverlappingSelectorsOnce(GameTestHelper context) {
        var mob = PickupTestSupport.skeleton(context);
        var server = context.getLevel().getServer();
        var resources = server.getResourceManager();
        var fixture = resources.getResource(OVERLAP).orElseThrow();
        var bag = MobInventories.inventory(mob);
        bag.insertAt(0, new ItemStack(Items.COBBLESTONE, OVERRIDE_LIMIT - 1));
        var ground = PickupTestSupport.ground(mob, new ItemStack(Items.COBBLESTONE, SURPLUS));
        try {
            MobInventoryFabric.WANTED.reload(server, id -> java.util.Optional.of(fixture));
            PickupTestSupport.scan(context, mob);
            context.assertTrue(bag.peek(0).getCount() == OVERRIDE_LIMIT && ground.getItem().getCount() == SURPLUS - 1,
                    "重叠 ID 与标签应归入首个类别并仅计算一次");
        } finally {
            MobInventoryFabric.WANTED.reload(server, resources);
        }
        context.succeed();
    }

    @GameTest
    public void followsGroundItemOutsidePickupReach(GameTestHelper context) {
        var mob = PickupTestSupport.skeleton(context);
        var start = mob.blockPosition();
        for (int offset = 0; offset <= TARGET_OFFSET; offset++) {
            context.getLevel().setBlockAndUpdate(start.east(offset).below(), Blocks.STONE.defaultBlockState());
        }
        var item = PickupTestSupport.ground(mob, new ItemStack(Items.BONE));
        var destination = Vec3.atBottomCenterOf(start.east(TARGET_OFFSET));
        item.setPos(destination);
        double startX = mob.getX();
        PickupTestSupport.tick(context, mob, SCAN * 2);
        context.assertTrue(!item.isAlive() && mob.getX() - startX > 1, "没有走向范围外的掉落物并拾取");
        context.succeed();
    }

    @GameTest
    public void wantedOnlyAndVanillaEquipmentCannotBypass(GameTestHelper context) {
        var mob = PickupTestSupport.skeleton(context);
        mob.setCanPickUpLoot(true /* canPickUpLoot */);
        var helmet = PickupTestSupport.ground(mob, new ItemStack(Items.IRON_HELMET));
        var ordinary = PickupTestSupport.ground(mob, new ItemStack(Items.ARROW));
        var stone = PickupTestSupport.ground(mob, new ItemStack(Items.COBBLESTONE));
        var potion = PickupTestSupport.ground(mob, new ItemStack(Items.POTION));
        var bones = PickupTestSupport.ground(mob, new ItemStack(Items.BONE, BONE_LIMIT + SURPLUS));
        PickupTestSupport.scan(context, mob);
        var stack = MobInventories.inventory(mob).peek(0);
        context.assertTrue(stack.is(Items.BONE) && stack.getCount() == BONE_LIMIT, "骨头类别或限额错误");
        context.assertTrue(bones.getItem().getCount() == SURPLUS, "骨头部分拾取的地面余量错误");
        context.assertTrue(helmet.isAlive() && ordinary.isAlive() && stone.isAlive() && potion.isAlive(),
                "原版装备或背囊捡走了想要表以外的物品");
        context.assertTrue(mob.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "原版自动装备绕过背囊");
        context.succeed();
    }

    @GameTest
    public void handThenPocketAndFullInventory(GameTestHelper context) {
        var mob = PickupTestSupport.skeleton(context);
        var bag = MobInventories.inventory(mob);
        int pocket = MobInventory.ROW_SIZE;
        bag.insertAt(pocket, new ItemStack(Items.BONE));
        PickupTestSupport.ground(mob, new ItemStack(Items.BONE));
        PickupTestSupport.scan(context, mob);
        context.assertTrue(bag.peek(0).is(Items.BONE) && bag.peek(pocket).getCount() == 1, "越过空手边补口袋");
        bag.exchange(0, new ItemStack(Items.STICK));
        for (int slot = 1; slot < MobInventory.SIZE; slot++) {
            if (slot != pocket) {
                bag.insertAt(slot, new ItemStack(Items.STICK));
            }
        }
        var bones = PickupTestSupport.ground(mob, new ItemStack(Items.BONE));
        var totem = PickupTestSupport.ground(mob, new ItemStack(Items.TOTEM_OF_UNDYING));
        PickupTestSupport.scan(context, mob);
        context.assertTrue(!bones.isAlive() && bag.peek(pocket).getCount() == 2, "手边满时没有补口袋");
        context.assertTrue(totem.isAlive(), "满背囊捡了不相容的物品");
        context.succeed();
    }

    @GameTest
    public void categoryCountsBothHandsAndBothKindsOfArrow(GameTestHelper context) {
        var mob = PickupTestSupport.skeleton(context);
        var bag = MobInventories.inventory(mob);
        mob.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.BOW));
        bag.insertAt(0, new ItemStack(Items.TIPPED_ARROW, STACK));
        bag.insertAt(1, new ItemStack(Items.SPECTRAL_ARROW, STACK - 1));
        var offered = PickupTestSupport.ground(mob, new ItemStack(Items.SPECTRAL_ARROW, SURPLUS));
        var bow = PickupTestSupport.ground(mob, new ItemStack(Items.BOW));
        bag.insertAt(MobInventory.ROW_SIZE, new ItemStack(Items.IRON_AXE));
        var sword = PickupTestSupport.ground(mob, new ItemStack(Items.IRON_SWORD));
        PickupTestSupport.scan(context, mob);
        context.assertTrue(offered.getItem().getCount() == SURPLUS - 1, "两种特制箭没有共用两组限额");
        context.assertTrue(bow.isAlive() && sword.isAlive(), "忽略副手弓或剑斧共同限额");
        context.succeed();
    }

    @GameTest
    public void griefingDelayAndReservation(GameTestHelper context) {
        var level = context.getLevel();
        var mob = PickupTestSupport.skeleton(context);
        var item = PickupTestSupport.ground(mob, new ItemStack(Items.BONE));
        var rules = level.getGameRules();
        boolean oldRule = rules.get(GameRules.MOB_GRIEFING);
        try {
            rules.set(GameRules.MOB_GRIEFING, false /* value */, level.getServer());
            PickupTestSupport.scan(context, mob);
            context.assertTrue(item.isAlive(), "关闭 mobGriefing 仍然拾取");
            rules.set(GameRules.MOB_GRIEFING, true /* value */, level.getServer());
            item.setPickUpDelay(SCAN);
            PickupTestSupport.scan(context, mob);
            context.assertTrue(item.isAlive(), "未守拾取延迟");
            item.setNoPickUpDelay();
            item.setTarget(UUID.randomUUID());
            PickupTestSupport.scan(context, mob);
            context.assertTrue(item.isAlive(), "抢走了保留给特定玩家的物品");
            item.setTarget(null);
            PickupTestSupport.scan(context, mob);
            context.assertTrue(!item.isAlive(), "限制解除后没有拾取");
        } finally {
            rules.set(GameRules.MOB_GRIEFING, oldRule, level.getServer());
        }
        context.succeed();
    }

    @GameTest
    public void competingSkeletonsConserveOneGroundStack(GameTestHelper context) {
        var first = PickupTestSupport.skeleton(context);
        var second = PickupTestSupport.skeleton(context);
        var item = PickupTestSupport.ground(first, new ItemStack(Items.BONE, BONE_LIMIT + SURPLUS));
        for (int tick = 0; tick < SCAN; tick++) {
            PickupTestSupport.tick(context, first, 1);
            PickupTestSupport.tick(context, second, 1);
        }
        int a = MobInventories.inventory(first).peek(0).getCount();
        int b = MobInventories.inventory(second).peek(0).getCount();
        context.assertTrue(!item.isAlive() && Math.min(a, b) == SURPLUS && Math.max(a, b) == BONE_LIMIT,
                "争抢同堆没有按提交时的实物余量拾取");
        context.succeed();
    }

    @GameTest
    public void freezingSpillsComponentsAndCount(GameTestHelper context) {
        var mob = PickupTestSupport.skeleton(context);
        var stack = new ItemStack(Items.BONE, SURPLUS);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("snow"));
        MobInventories.inventory(mob).insertAt(MobInventory.ROW_SIZE, stack.copy());
        context.setBlock(InventoryTestSupport.SPAWN, Blocks.POWDER_SNOW);
        PickupTestSupport.tick(context, mob, 1);
        mob.startFreezeConversion(0);
        PickupTestSupport.tick(context, mob, 1);
        var area = mob.getBoundingBox().inflate(1);
        var level = context.getLevel();
        var strays = level.getEntities(EntityTypes.STRAY, area, stray -> true);
        var drops = level.getEntitiesOfClass(ItemEntity.class, area, item -> ItemStack.matches(stack, item.getItem()));
        context.assertTrue(mob.isRemoved() && strays.size() == 1, "真实细雪转化没有产生流浪者");
        context.assertTrue(drops.size() == 1, "转化应恰好掉出一份原组件与数量");
        context.succeed();
    }
}
